package com.codgo.ulock.tenant;

import static com.codgo.ulock.support.Api.DEFAULT_PASSWORD;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codgo.ulock.support.Api;
import com.codgo.ulock.support.Api.TenantFixture;
import com.codgo.ulock.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantIntegrationTest extends IntegrationTest {

    private static final String PLATFORM_TENANT_ID = "00000000-0000-0000-0000-000000000001";

    @Test
    void creatingATenantProvisionsReservedRolesAndAWorkingFirstAdmin() throws Exception {
        String slug = Api.unique("acme");
        var created = api.post(api.platformAdminToken(), "/api/v1/tenants", Map.of(
                        "name", "Acme", "slug", slug,
                        "admin", Map.of("email", "boss@acme.test", "fullName", "Boss", "password", DEFAULT_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        String tenantId = api.body(created).get("id").asText();

        String adminToken = api.login(slug, "boss@acme.test", DEFAULT_PASSWORD).accessToken();
        api.get(adminToken, "/api/v1/tenants/" + tenantId + "/roles")
                .andExpect(jsonPath("$.content[*].name", hasItems("TENANT_ADMIN", "TENANT_AUDITOR")))
                .andExpect(jsonPath("$.content[?(@.name == 'TENANT_ADMIN')].reserved").value(true));
    }

    @Test
    void tenantCreationIsAudited() throws Exception {
        TenantFixture tenant = api.createTenant();

        api.get(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/audit-logs?action=TENANT_CREATED")
                .andExpect(jsonPath("$.content[0].details.slug").value(tenant.slug()))
                .andExpect(jsonPath("$.content[0].actorUserId").isNotEmpty());
    }

    @Test
    void slugsAreUnique() throws Exception {
        TenantFixture tenant = api.createTenant();

        api.post(api.platformAdminToken(), "/api/v1/tenants", Map.of("name", "Copy", "slug", tenant.slug(),
                        "admin", Map.of("email", "a@b.test", "fullName", "A", "password", DEFAULT_PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void validatesSlugAndRequiresAnInitialAdmin() throws Exception {
        api.post(api.platformAdminToken(), "/api/v1/tenants", Map.of("name", "Bad", "slug", "Not A Slug!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("slug", "admin")));
    }

    @Test
    void rejectsAWeakInitialAdminPasswordWithoutCreatingTheTenant() throws Exception {
        String slug = Api.unique("weak");
        api.post(api.platformAdminToken(), "/api/v1/tenants", Map.of("name", "Weak", "slug", slug,
                        "admin", Map.of("email", "a@b.test", "fullName", "A", "password", "short")))
                .andExpect(status().isBadRequest());

        api.loginRequest(slug, "a@b.test", "short").andExpect(status().isUnauthorized());
    }

    @Test
    void listIsPaginatedAndFilterableByStatus() throws Exception {
        TenantFixture suspended = api.createTenant();
        api.patch(api.platformAdminToken(), "/api/v1/tenants/" + suspended.id(), Map.of("status", "SUSPENDED"))
                .andExpect(status().isOk());

        api.get(api.platformAdminToken(), "/api/v1/tenants?size=1")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.size").value(1));
        api.get(api.platformAdminToken(), "/api/v1/tenants?status=SUSPENDED&sort=updatedAt,desc&size=5")
                .andExpect(jsonPath("$.content[*].status", everyItem(is("SUSPENDED"))))
                .andExpect(jsonPath("$.content[0].id").value(suspended.id().toString()));
    }

    @Test
    void updateRenamesAndSuspends() throws Exception {
        TenantFixture tenant = api.createTenant();

        api.patch(api.platformAdminToken(), "/api/v1/tenants/" + tenant.id(), Map.of("name", "Renamed", "status", "SUSPENDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.slug").value(tenant.slug()));

        api.get(api.platformAdminToken(), "/api/v1/tenants/" + tenant.id()).andExpect(status().isOk());
        String auditAsPlatformAuditor = "/api/v1/tenants/" + tenant.id() + "/audit-logs?action=TENANT_UPDATED";
        api.patch(api.platformAdminToken(), "/api/v1/tenants/" + tenant.id(), Map.of("status", "ACTIVE"))
                .andExpect(status().isOk());
        api.get(tenant.adminToken(), auditAsPlatformAuditor)
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[1].details.name").value("Renamed"))
                .andExpect(jsonPath("$.content[1].details.status").value("SUSPENDED"));
    }

    @Test
    void thePlatformTenantCannotBeSuspended() throws Exception {
        api.patch(api.platformAdminToken(), "/api/v1/tenants/" + PLATFORM_TENANT_ID, Map.of("status", "SUSPENDED"))
                .andExpect(status().isConflict());
    }

    @Test
    void tenantAdminsCanReadOnlyTheirOwnTenantAndCannotManageTenants() throws Exception {
        TenantFixture tenant = api.createTenant();
        TenantFixture other = api.createTenant();

        api.get(tenant.adminToken(), "/api/v1/tenants/" + tenant.id()).andExpect(status().isOk());
        api.get(tenant.adminToken(), "/api/v1/tenants/" + other.id()).andExpect(status().isForbidden());
        api.get(tenant.adminToken(), "/api/v1/tenants").andExpect(status().isForbidden());
        api.patch(tenant.adminToken(), "/api/v1/tenants/" + tenant.id(), Map.of("name", "Mine now"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownTenantsAreNotFound() throws Exception {
        api.get(api.platformAdminToken(), "/api/v1/tenants/" + UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unknownSortPropertiesAreABadRequest() throws Exception {
        api.get(api.platformAdminToken(), "/api/v1/tenants?sort=nope").andExpect(status().isBadRequest());
    }
}
