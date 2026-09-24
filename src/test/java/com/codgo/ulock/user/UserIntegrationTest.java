package com.codgo.ulock.user;

import static com.codgo.ulock.support.Api.DEFAULT_PASSWORD;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codgo.ulock.support.Api.TenantFixture;
import com.codgo.ulock.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserIntegrationTest extends IntegrationTest {

    TenantFixture tenant;
    String users;

    @BeforeEach
    void setUp() throws Exception {
        tenant = api.createTenant();
        users = "/api/v1/tenants/" + tenant.id() + "/users";
    }

    @Test
    void createsAUserWithANormalisedEmailAndNoPasswordInTheResponse() throws Exception {
        api.post(tenant.adminToken(), users, Map.of("email", "Jane.Doe@Example.TEST", "fullName", "Jane Doe",
                        "password", DEFAULT_PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("jane.doe@example.test"))
                .andExpect(jsonPath("$.tenantId").value(tenant.id().toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        api.get(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/audit-logs?action=USER_CREATED")
                .andExpect(jsonPath("$.content[*].details.email", hasItem("jane.doe@example.test")));
    }

    @Test
    void emailIsUniqueWithinATenantButNotGlobally() throws Exception {
        api.createUser(tenant, "dup@example.test");
        api.post(tenant.adminToken(), users, Map.of("email", "DUP@example.test", "fullName", "Again",
                "password", DEFAULT_PASSWORD)).andExpect(status().isConflict());

        TenantFixture other = api.createTenant();
        api.createUser(other, "dup@example.test");
    }

    @Test
    void enforcesThePasswordPolicy() throws Exception {
        api.post(tenant.adminToken(), users, Map.of("email", "short@example.test", "fullName", "Short",
                        "password", "123456789"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Password must be at least 10 characters"));
    }

    @Test
    void validatesThePayload() throws Exception {
        api.post(tenant.adminToken(), users, Map.of("email", "not-an-email", "fullName", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void listIsPaginatedSortedAndFilterable() throws Exception {
        UUID inactive = api.createUser(tenant, "b@example.test");
        api.createUser(tenant, "a@example.test");
        api.patch(tenant.adminToken(), users + "/" + inactive, Map.of("status", "INACTIVE")).andExpect(status().isOk());

        api.get(tenant.adminToken(), users + "?size=2&sort=email,asc")
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].email").value("a@example.test"));
        api.get(tenant.adminToken(), users + "?status=INACTIVE")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[*].status", everyItem(is("INACTIVE"))));
    }

    @Test
    void renamesAndAuditsOnlyRealChanges() throws Exception {
        UUID userId = api.createUser(tenant, "rename@example.test");

        api.patch(tenant.adminToken(), users + "/" + userId, Map.of("fullName", "New Name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("New Name"));
        api.patch(tenant.adminToken(), users + "/" + userId, Map.of("fullName", "New Name")).andExpect(status().isOk());

        api.get(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/audit-logs?action=USER_RENAMED")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].details.fullName").value("New Name"));
    }

    @Test
    void statusChangesAreAuditedAsExplicitActions() throws Exception {
        UUID userId = api.createUser(tenant, "status@example.test");
        String user = users + "/" + userId;

        api.patch(tenant.adminToken(), user, Map.of("status", "INACTIVE")).andExpect(jsonPath("$.status").value("INACTIVE"));
        api.patch(tenant.adminToken(), user, Map.of("status", "INACTIVE")).andExpect(status().isOk());
        api.patch(tenant.adminToken(), user, Map.of("status", "ACTIVE")).andExpect(jsonPath("$.status").value("ACTIVE"));

        String audit = "/api/v1/tenants/" + tenant.id() + "/audit-logs?userId=" + tenant.adminId() + "&action=";
        api.get(tenant.adminToken(), audit + "USER_DEACTIVATED")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].targetId").value(userId.toString()));
        api.get(tenant.adminToken(), audit + "USER_ACTIVATED").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void adminsCannotDeactivateThemselves() throws Exception {
        api.patch(tenant.adminToken(), users + "/" + tenant.adminId(), Map.of("status", "INACTIVE"))
                .andExpect(status().isConflict());
    }

    @Test
    void passwordResetReplacesThePasswordAndIsAuditedWithoutIt() throws Exception {
        UUID userId = api.createUser(tenant, "reset@example.test");

        api.post(tenant.adminToken(), users + "/" + userId + "/password-reset", Map.of("newPassword", "fresh-password-99"))
                .andExpect(status().isNoContent());

        api.loginRequest(tenant.slug(), "reset@example.test", DEFAULT_PASSWORD).andExpect(status().isUnauthorized());
        api.loginRequest(tenant.slug(), "reset@example.test", "fresh-password-99").andExpect(status().isOk());
        api.get(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/audit-logs?action=USER_PASSWORD_RESET")
                .andExpect(jsonPath("$.content[0].targetId").value(userId.toString()))
                .andExpect(jsonPath("$.content[0].details").doesNotExist());
    }

    @Test
    void unknownUsersAreNotFound() throws Exception {
        api.get(tenant.adminToken(), users + "/" + UUID.randomUUID()).andExpect(status().isNotFound());
        api.patch(tenant.adminToken(), users + "/" + UUID.randomUUID(), Map.of("fullName", "x"))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedIdsAreABadRequest() throws Exception {
        api.get(tenant.adminToken(), users + "/not-a-uuid").andExpect(status().isBadRequest());
    }

    @Test
    void usersWithoutUserPermissionsAreForbidden() throws Exception {
        api.createUser(tenant, "plain@example.test");
        String token = api.login(tenant.slug(), "plain@example.test", DEFAULT_PASSWORD).accessToken();

        api.get(token, users).andExpect(status().isForbidden());
        api.post(token, users, Map.of("email", "x@example.test", "fullName", "X", "password", DEFAULT_PASSWORD))
                .andExpect(status().isForbidden());
    }
}
