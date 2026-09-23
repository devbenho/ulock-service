package com.codgo.ulock.role;

import static com.codgo.ulock.support.Api.DEFAULT_PASSWORD;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codgo.ulock.support.Api.TenantFixture;
import com.codgo.ulock.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoleIntegrationTest extends IntegrationTest {

    /** System permission ids, fixed by V2__seed_system_data.sql. */
    private static final String TENANT_WRITE_ID = "00000000-0000-0000-0001-000000000002";
    private static final String USER_READ_ID = "00000000-0000-0000-0001-000000000004";

    TenantFixture tenant;
    String base;

    @BeforeEach
    void setUp() throws Exception {
        tenant = api.createTenant();
        base = "/api/v1/tenants/" + tenant.id();
    }

    @Test
    void createsAndReadsARole() throws Exception {
        String location = api.post(tenant.adminToken(), base + "/roles", Map.of("name", "Editor", "description", "Edits"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Editor"))
                .andExpect(jsonPath("$.reserved").value(false))
                .andExpect(jsonPath("$.permissions").isEmpty())
                .andReturn().getResponse().getHeader("Location");

        api.get(tenant.adminToken(), location).andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Edits"));
    }

    @Test
    void roleNamesAreUniquePerTenantAndReservedNamesAreRefused() throws Exception {
        api.createRole(tenant, "Editor");

        api.post(tenant.adminToken(), base + "/roles", Map.of("name", "editor")).andExpect(status().isConflict());
        api.post(tenant.adminToken(), base + "/roles", Map.of("name", "PLATFORM_ADMIN")).andExpect(status().isConflict());
        api.createRole(api.createTenant(), "Editor");
    }

    @Test
    void replacesPermissionsWithTenantAndSystemPermissions() throws Exception {
        UUID approve = api.createPermission(tenant, "invoice:approve");
        UUID roleId = api.createRole(tenant, "Approver");

        api.put(tenant.adminToken(), base + "/roles/" + roleId + "/permissions",
                        Map.of("permissionIds", List.of(approve, USER_READ_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[*].code", contains("invoice:approve", "user:read")));

        api.put(tenant.adminToken(), base + "/roles/" + roleId + "/permissions", Map.of("permissionIds", List.of()))
                .andExpect(jsonPath("$.permissions").isEmpty());
        api.get(tenant.adminToken(), base + "/audit-logs?action=ROLE_PERMISSIONS_REPLACED")
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void rolesCannotUseAnotherTenantsPermissionsOrPlatformOnlyPermissions() throws Exception {
        UUID foreign = api.createPermission(api.createTenant(), "invoice:approve");
        UUID roleId = api.createRole(tenant, "Sneaky");
        String path = base + "/roles/" + roleId + "/permissions";

        api.put(tenant.adminToken(), path, Map.of("permissionIds", List.of(foreign)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown permission ids: " + foreign));
        api.put(tenant.adminToken(), path, Map.of("permissionIds", List.of(TENANT_WRITE_ID)))
                .andExpect(status().isBadRequest());
        api.put(tenant.adminToken(), path, Map.of("permissionIds", List.of(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reservedRolesCannotBeChanged() throws Exception {
        UUID adminRole = api.roleId(tenant, "TENANT_ADMIN");
        String path = base + "/roles/" + adminRole;

        api.patch(tenant.adminToken(), path, Map.of("name", "Boss")).andExpect(status().isConflict());
        api.put(tenant.adminToken(), path + "/permissions", Map.of("permissionIds", List.of())).andExpect(status().isConflict());
        api.delete(tenant.adminToken(), path).andExpect(status().isConflict());
    }

    @Test
    void updatesARole() throws Exception {
        UUID roleId = api.createRole(tenant, "Old");

        api.patch(tenant.adminToken(), base + "/roles/" + roleId, Map.of("name", "New", "description", "Better"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"))
                .andExpect(jsonPath("$.description").value("Better"));
        api.patch(tenant.adminToken(), base + "/roles/" + roleId, Map.of("name", "TENANT_AUDITOR"))
                .andExpect(status().isConflict());
    }

    @Test
    void renamesMayChangeOnlyTheCaseAndMustNotBeBlank() throws Exception {
        UUID roleId = api.createRole(tenant, "Editor");

        api.patch(tenant.adminToken(), base + "/roles/" + roleId, Map.of("name", "EDITOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("EDITOR"));
        api.patch(tenant.adminToken(), base + "/roles/" + roleId, Map.of("name", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rolesWithMembersCannotBeDeleted() throws Exception {
        UUID roleId = api.createRole(tenant, "Busy");
        UUID userId = api.createUser(tenant, "member@example.test");
        api.assignRole(tenant, userId, roleId);

        api.delete(tenant.adminToken(), base + "/roles/" + roleId).andExpect(status().isConflict());

        api.delete(tenant.adminToken(), base + "/users/" + userId + "/roles/" + roleId).andExpect(status().isNoContent());
        api.delete(tenant.adminToken(), base + "/roles/" + roleId).andExpect(status().isNoContent());
        api.get(tenant.adminToken(), base + "/roles/" + roleId).andExpect(status().isNotFound());
    }

    @Test
    void assignmentIsIdempotentRevocationOfAMissingAssignmentIsNotFound() throws Exception {
        UUID roleId = api.createRole(tenant, "Viewer");
        UUID userId = api.createUser(tenant, "viewer@example.test");

        api.assignRole(tenant, userId, roleId);
        api.assignRole(tenant, userId, roleId);
        api.get(tenant.adminToken(), base + "/audit-logs?action=ROLE_ASSIGNED&userId=" + tenant.adminId())
                .andExpect(jsonPath("$.content[?(@.targetId == '" + userId + "')]").value(org.hamcrest.Matchers.hasSize(1)));

        api.delete(tenant.adminToken(), base + "/users/" + userId + "/roles/" + roleId).andExpect(status().isNoContent());
        api.delete(tenant.adminToken(), base + "/users/" + userId + "/roles/" + roleId).andExpect(status().isNotFound());
    }

    @Test
    void cannotAssignRolesAcrossTenants() throws Exception {
        TenantFixture other = api.createTenant();
        UUID foreignRole = api.createRole(other, "Foreign");
        UUID userId = api.createUser(tenant, "local@example.test");

        api.post(tenant.adminToken(), base + "/users/" + userId + "/roles/" + foreignRole, null)
                .andExpect(status().isNotFound());
    }

    @Test
    void listsRoleMembers() throws Exception {
        UUID roleId = api.createRole(tenant, "Team");
        UUID userId = api.createUser(tenant, "teammate@example.test");
        api.assignRole(tenant, userId, roleId);

        api.get(tenant.adminToken(), base + "/roles/" + roleId + "/members")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("teammate@example.test"));
    }

    @Test
    void accessSummaryShowsRolesAndTheUnionOfTheirPermissions() throws Exception {
        UUID approve = api.createPermission(tenant, "invoice:approve");
        UUID pay = api.createPermission(tenant, "invoice:pay");
        UUID approver = api.createRole(tenant, "Approver", approve, UUID.fromString(USER_READ_ID));
        UUID payer = api.createRole(tenant, "Payer", pay, approve);
        UUID userId = api.createUser(tenant, "finance@example.test");
        api.assignRole(tenant, userId, approver);
        api.assignRole(tenant, userId, payer);

        api.get(tenant.adminToken(), base + "/users/" + userId + "/access-summary")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("finance@example.test"))
                .andExpect(jsonPath("$.roles[*].name", contains("Approver", "Payer")))
                .andExpect(jsonPath("$.effectivePermissions", contains("invoice:approve", "invoice:pay", "user:read")));

        api.patch(tenant.adminToken(), base + "/users/" + userId, Map.of("status", "INACTIVE")).andExpect(status().isOk());
        api.get(tenant.adminToken(), base + "/users/" + userId + "/access-summary")
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.effectivePermissions").isEmpty());
    }

    @Test
    void customRolesGrantAccessToUlocksOwnApi() throws Exception {
        UUID readOnly = api.createRole(tenant, "ReadOnly", UUID.fromString(USER_READ_ID));
        UUID userId = api.createUser(tenant, "reader@example.test");
        api.assignRole(tenant, userId, readOnly);
        String token = api.login(tenant.slug(), "reader@example.test", DEFAULT_PASSWORD).accessToken();

        api.get(token, base + "/users").andExpect(status().isOk());
        api.patch(token, base + "/users/" + userId, Map.of("fullName", "Promoted")).andExpect(status().isForbidden());
        api.get(token, base + "/roles").andExpect(status().isForbidden());
    }

    @Test
    void permissionsListShowsTenantAndUsableSystemPermissionsOnly() throws Exception {
        api.createPermission(tenant, "invoice:approve");
        api.createPermission(api.createTenant(), "invoice:reject");

        api.get(tenant.adminToken(), base + "/permissions?size=100")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].code", hasItems("invoice:approve", "user:read", "audit:read")))
                .andExpect(jsonPath("$.content[*].code", not(hasItem("invoice:reject"))))
                .andExpect(jsonPath("$.content[*].code", not(hasItem("tenant:write"))))
                .andExpect(jsonPath("$.content[?(@.code == 'user:read')].system").value(true))
                .andExpect(jsonPath("$.content[?(@.code == 'invoice:approve')].system").value(false));
    }

    @Test
    void permissionCodesAreValidatedAndCannotShadowSystemOrExistingCodes() throws Exception {
        api.createPermission(tenant, "report:export");

        api.post(tenant.adminToken(), base + "/permissions", Map.of("code", "report:export")).andExpect(status().isConflict());
        api.post(tenant.adminToken(), base + "/permissions", Map.of("code", "user:read")).andExpect(status().isConflict());
        api.post(tenant.adminToken(), base + "/permissions", Map.of("code", "NotValid")).andExpect(status().isBadRequest());
        api.post(tenant.adminToken(), base + "/permissions", Map.of("code", "SCOPE_authz:check"))
                .andExpect(status().isBadRequest());
    }
}
