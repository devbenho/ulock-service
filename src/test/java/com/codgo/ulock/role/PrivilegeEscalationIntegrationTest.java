package com.codgo.ulock.role;

import static com.codgo.ulock.support.Api.DEFAULT_PASSWORD;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codgo.ulock.support.Api.TenantFixture;
import com.codgo.ulock.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Delegated admins can only grant, revoke or administer system permissions they hold themselves. */
class PrivilegeEscalationIntegrationTest extends IntegrationTest {

    private static final UUID USER_READ = UUID.fromString("00000000-0000-0000-0001-000000000004");
    private static final UUID USER_WRITE = UUID.fromString("00000000-0000-0000-0001-000000000005");
    private static final UUID ROLE_READ = UUID.fromString("00000000-0000-0000-0001-000000000006");
    private static final UUID ROLE_WRITE = UUID.fromString("00000000-0000-0000-0001-000000000007");
    private static final UUID AUDIT_READ = UUID.fromString("00000000-0000-0000-0001-000000000008");

    TenantFixture tenant;
    String base;

    @BeforeEach
    void setUp() throws Exception {
        tenant = api.createTenant();
        base = "/api/v1/tenants/" + tenant.id();
    }

    @Test
    void helpdeskCanResetOrdinaryUsersButNotAdmins() throws Exception {
        String helpdesk = delegate("helpdesk@example.test", USER_READ, USER_WRITE);
        UUID ordinary = api.createUser(tenant, "ordinary@example.test");

        api.post(helpdesk, base + "/users/" + ordinary + "/password-reset", Map.of("newPassword", "reset-by-helpdesk"))
                .andExpect(status().isNoContent());
        api.post(helpdesk, base + "/users/" + tenant.adminId() + "/password-reset", Map.of("newPassword", "hijacked-admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You cannot manage a user who holds permissions you do not hold"));
        api.patch(helpdesk, base + "/users/" + tenant.adminId(), Map.of("status", "INACTIVE"))
                .andExpect(status().isForbidden());
        api.loginRequest(tenant.slug(), tenant.adminEmail(), DEFAULT_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void roleManagersCannotHandOutReservedRolesOrPermissionsTheyLack() throws Exception {
        String manager = delegate("manager@example.test", USER_READ, ROLE_READ, ROLE_WRITE);
        UUID managerId = userId("manager@example.test");
        UUID adminRole = api.roleId(tenant, "TENANT_ADMIN");
        UUID auditorRole = api.roleId(tenant, "TENANT_AUDITOR");

        api.post(manager, base + "/users/" + managerId + "/roles/" + adminRole, null).andExpect(status().isForbidden());
        api.post(manager, base + "/users/" + managerId + "/roles/" + auditorRole, null).andExpect(status().isForbidden());
        api.delete(manager, base + "/users/" + tenant.adminId() + "/roles/" + adminRole).andExpect(status().isForbidden());

        UUID custom = api.createRole(tenant, "Custom");
        api.put(manager, base + "/roles/" + custom + "/permissions", Map.of("permissionIds", List.of(AUDIT_READ)))
                .andExpect(status().isForbidden());
        api.put(manager, base + "/roles/" + custom + "/permissions", Map.of("permissionIds", List.of(USER_READ)))
                .andExpect(status().isOk());
    }

    @Test
    void roleManagersStillManageBusinessPermissionsAndRoles() throws Exception {
        String manager = delegate("biz@example.test", USER_READ, ROLE_READ, ROLE_WRITE);
        UUID approve = api.createPermission(tenant, "invoice:approve");
        UUID clerk = api.createUser(tenant, "clerk@example.test");
        UUID approver = api.createRole(tenant, "Approver");

        api.put(manager, base + "/roles/" + approver + "/permissions", Map.of("permissionIds", List.of(approve)))
                .andExpect(status().isOk());
        api.post(manager, base + "/users/" + clerk + "/roles/" + approver, null).andExpect(status().isNoContent());
        api.delete(manager, base + "/users/" + clerk + "/roles/" + approver).andExpect(status().isNoContent());
    }

    private String delegate(String email, UUID... permissions) throws Exception {
        UUID userId = api.createUser(tenant, email);
        UUID roleId = api.createRole(tenant, "Delegate " + email, permissions);
        api.assignRole(tenant, userId, roleId);
        return api.login(tenant.slug(), email, DEFAULT_PASSWORD).accessToken();
    }

    private UUID userId(String email) throws Exception {
        var users = api.body(api.get(tenant.adminToken(), base + "/users?size=100")).get("content");
        for (var user : users) {
            if (user.get("email").asText().equals(email)) {
                return UUID.fromString(user.get("id").asText());
            }
        }
        throw new AssertionError(email);
    }
}
