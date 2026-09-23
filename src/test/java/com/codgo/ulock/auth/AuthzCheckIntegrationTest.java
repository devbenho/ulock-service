package com.codgo.ulock.auth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codgo.ulock.support.Api.ClientFixture;
import com.codgo.ulock.support.Api.TenantFixture;
import com.codgo.ulock.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class AuthzCheckIntegrationTest extends IntegrationTest {

    TenantFixture tenant;
    ClientFixture client;
    UUID userId;
    UUID roleId;

    @BeforeEach
    void setUp() throws Exception {
        tenant = api.createTenant();
        client = api.createMachineClient();
        userId = api.createUser(tenant, "clerk@example.test");
        UUID approve = api.createPermission(tenant, "invoice:approve");
        roleId = api.createRole(tenant, "Approver", approve);
        api.assignRole(tenant, userId, roleId);
    }

    @Test
    void allowsAPermissionGrantedThroughARole() throws Exception {
        check(client.accessToken(), tenant.id(), userId, "invoice:approve")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOW"));
    }

    @Test
    void allowsSystemPermissionsHeldThroughReservedRoles() throws Exception {
        check(client.accessToken(), tenant.id(), tenant.adminId(), "user:write")
                .andExpect(jsonPath("$.decision").value("ALLOW"));
    }

    @Test
    void deniesPermissionsTheUserDoesNotHold() throws Exception {
        check(client.accessToken(), tenant.id(), userId, "invoice:delete").andExpect(jsonPath("$.decision").value("DENY"));
    }

    @Test
    void deniesWhenTheUserBelongsToAnotherTenant() throws Exception {
        TenantFixture other = api.createTenant();
        check(client.accessToken(), other.id(), userId, "invoice:approve").andExpect(jsonPath("$.decision").value("DENY"));
    }

    @Test
    void deniesUnknownUsers() throws Exception {
        check(client.accessToken(), tenant.id(), UUID.randomUUID(), "invoice:approve")
                .andExpect(jsonPath("$.decision").value("DENY"));
    }

    @Test
    void revokingTheRoleDeniesImmediately() throws Exception {
        api.delete(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/users/" + userId + "/roles/" + roleId)
                .andExpect(status().isNoContent());

        check(client.accessToken(), tenant.id(), userId, "invoice:approve").andExpect(jsonPath("$.decision").value("DENY"));
    }

    @Test
    void deniesInactiveUsers() throws Exception {
        api.patch(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/users/" + userId, Map.of("status", "INACTIVE"))
                .andExpect(status().isOk());

        check(client.accessToken(), tenant.id(), userId, "invoice:approve").andExpect(jsonPath("$.decision").value("DENY"));
    }

    @Test
    void deniesUsersOfSuspendedTenants() throws Exception {
        api.patch(api.platformAdminToken(), "/api/v1/tenants/" + tenant.id(), Map.of("status", "SUSPENDED"))
                .andExpect(status().isOk());

        check(client.accessToken(), tenant.id(), userId, "invoice:approve").andExpect(jsonPath("$.decision").value("DENY"));
    }

    @Test
    void requiresAMachineClientToken() throws Exception {
        check(null, tenant.id(), userId, "invoice:approve").andExpect(status().isUnauthorized());
        check(tenant.adminToken(), tenant.id(), userId, "invoice:approve").andExpect(status().isForbidden());
    }

    @Test
    void validatesThePayload() throws Exception {
        api.post(client.accessToken(), "/api/v1/authz/check", Map.of("tenantId", tenant.id().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    private ResultActions check(String token, UUID tenantId, UUID userId, String permission) throws Exception {
        return api.post(token, "/api/v1/authz/check",
                Map.of("tenantId", tenantId.toString(), "userId", userId.toString(), "permission", permission));
    }
}
