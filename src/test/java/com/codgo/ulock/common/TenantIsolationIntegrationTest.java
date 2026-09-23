package com.codgo.ulock.common;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codgo.ulock.support.Api;
import com.codgo.ulock.support.Api.ClientFixture;
import com.codgo.ulock.support.Api.TenantFixture;
import com.codgo.ulock.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;

/**
 * Every tenant-scoped endpoint, called with a token from another tenant, must return 403 and leave
 * an audit record. {t}, {u} and {r} are the victim tenant's id, a user and a role in it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantIsolationIntegrationTest extends IntegrationTest {

    private static final String PLATFORM_TENANT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String ISOLATION_DENIED = "Access to this tenant is not allowed";

    TenantFixture attacker;
    TenantFixture victim;
    UUID victimUser;
    UUID victimRole;
    ClientFixture machineClient;

    @BeforeAll
    void setUp() throws Exception {
        attacker = api.createTenant();
        victim = api.createTenant();
        victimUser = api.createUser(victim, "victim@example.test");
        victimRole = api.createRole(victim, "VictimRole");
        machineClient = api.createMachineClient();
    }

    static Stream<Arguments> tenantScopedEndpoints() {
        Map<String, Object> user = Map.of("email", "x@example.test", "fullName", "X", "password", "long-enough-password");
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/users", user),
                Arguments.of(HttpMethod.GET, "/users", null),
                Arguments.of(HttpMethod.GET, "/users/{u}", null),
                Arguments.of(HttpMethod.PATCH, "/users/{u}", Map.of("fullName", "Pwned")),
                Arguments.of(HttpMethod.POST, "/users/{u}/password-reset", Map.of("newPassword", "attacker-password")),
                Arguments.of(HttpMethod.POST, "/roles", Map.of("name", "Evil")),
                Arguments.of(HttpMethod.GET, "/roles", null),
                Arguments.of(HttpMethod.GET, "/roles/{r}", null),
                Arguments.of(HttpMethod.PATCH, "/roles/{r}", Map.of("name", "Evil")),
                Arguments.of(HttpMethod.DELETE, "/roles/{r}", null),
                Arguments.of(HttpMethod.PUT, "/roles/{r}/permissions", Map.of("permissionIds", List.of())),
                Arguments.of(HttpMethod.GET, "/roles/{r}/members", null),
                Arguments.of(HttpMethod.POST, "/users/{u}/roles/{r}", null),
                Arguments.of(HttpMethod.DELETE, "/users/{u}/roles/{r}", null),
                Arguments.of(HttpMethod.GET, "/users/{u}/access-summary", null),
                Arguments.of(HttpMethod.POST, "/permissions", Map.of("code", "evil:do")),
                Arguments.of(HttpMethod.GET, "/permissions", null),
                Arguments.of(HttpMethod.GET, "/audit-logs", null));
    }

    @ParameterizedTest(name = "{0} /tenants/'{'t'}'{1}")
    @MethodSource("tenantScopedEndpoints")
    void anotherTenantsTokenIsForbiddenAndAudited(HttpMethod method, String path, Object body) throws Exception {
        String resolved = victimPath(path);

        api.request(method, attacker.adminToken(), resolved, body)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(ISOLATION_DENIED));

        String auditRecord = "$.content[?(@.details.method == '" + method.name() + "' && @.details.path == '" + resolved
                + "' && @.targetId == '" + victim.id() + "')]";
        api.get(attacker.adminToken(), "/api/v1/tenants/" + attacker.id() + "/audit-logs?action=TENANT_ACCESS_DENIED&size=100")
                .andExpect(jsonPath(auditRecord, hasSize(1)));
    }

    @ParameterizedTest(name = "{0} /tenants/'{'t'}'{1}")
    @MethodSource("tenantScopedEndpoints")
    void platformAdminsAreBoundByTenantIsolationToo(HttpMethod method, String path, Object body) throws Exception {
        api.request(method, api.platformAdminToken(), victimPath(path), body)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(ISOLATION_DENIED));
    }

    @ParameterizedTest(name = "{0} /tenants/'{'t'}'{1}")
    @MethodSource("tenantScopedEndpoints")
    void machineClientTokensCannotReachTenantResources(HttpMethod method, String path, Object body) throws Exception {
        api.request(method, machineClient.accessToken(), victimPath(path), body)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(ISOLATION_DENIED));
    }

    /** Regression: the UUID binder trims whitespace, so a padded id must not bypass the check. */
    @ParameterizedTest
    @ValueSource(strings = {"%20", "%09", "%0A"})
    void whitespacePaddedTenantIdsCannotBypassIsolation(String padding) throws Exception {
        String padded = "/api/v1/tenants/" + padding + PLATFORM_TENANT_ID + padding + "/users";
        String intruder = Api.unique("intruder") + "@example.test";
        Map<String, Object> user = Map.of("email", intruder, "fullName", "Intruder", "password", "long-enough-password");

        // 403 from the isolation check, or 400 where the id is rejected earlier: never a success.
        api.requestRaw(HttpMethod.POST, attacker.adminToken(), padded, user).andExpect(status().is4xxClientError());
        api.requestRaw(HttpMethod.GET, attacker.adminToken(), padded, null).andExpect(status().is4xxClientError());

        api.get(api.platformAdminToken(), "/api/v1/tenants/" + PLATFORM_TENANT_ID + "/users?size=100")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].email", not(hasItem(intruder))));
    }

    @Test
    void spacePaddedTenantIdsAreRejectedByTheIsolationCheck() throws Exception {
        api.requestRaw(HttpMethod.GET, attacker.adminToken(), "/api/v1/tenants/%20" + victim.id() + "/users", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(ISOLATION_DENIED));
    }

    @Test
    void unparseableTenantIdsFailClosed() throws Exception {
        api.get(attacker.adminToken(), "/api/v1/tenants/not-a-uuid/users").andExpect(status().isBadRequest());
        api.get(attacker.adminToken(), "/api/v1/tenants/not-a-uuid").andExpect(status().isBadRequest());
    }

    @Test
    void readingAnotherTenantAsATenantAdminIsForbiddenAndAudited() throws Exception {
        String path = "/api/v1/tenants/" + victim.id();

        api.get(attacker.adminToken(), path)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(ISOLATION_DENIED));
        api.get(attacker.adminToken(), "/api/v1/tenants/" + attacker.id() + "/audit-logs?action=TENANT_ACCESS_DENIED&size=100")
                .andExpect(jsonPath("$.content[?(@.details.method == 'GET' && @.details.path == '" + path + "')]", hasSize(1)));
    }

    @Test
    void machineClientIsolationDenialsAreAuditedInThePlatformTenant() throws Exception {
        String path = victimPath("/users/{u}/access-summary");
        api.get(machineClient.accessToken(), path).andExpect(status().isForbidden());

        api.get(api.platformAdminToken(), "/api/v1/tenants/" + PLATFORM_TENANT_ID + "/audit-logs?action=TENANT_ACCESS_DENIED&size=100")
                .andExpect(jsonPath("$.content[?(@.details.clientId == '" + machineClient.clientId() + "')]",
                        not(empty())));
    }

    static Stream<Arguments> victimResourcesUnderOwnTenantPath() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/users/{u}", null),
                Arguments.of(HttpMethod.PATCH, "/users/{u}", Map.of("fullName", "Pwned")),
                Arguments.of(HttpMethod.POST, "/users/{u}/password-reset", Map.of("newPassword", "attacker-password")),
                Arguments.of(HttpMethod.GET, "/users/{u}/access-summary", null),
                Arguments.of(HttpMethod.POST, "/users/{u}/roles/{r}", null),
                Arguments.of(HttpMethod.DELETE, "/users/{u}/roles/{r}", null),
                Arguments.of(HttpMethod.POST, "/users/{self}/roles/{r}", null),
                Arguments.of(HttpMethod.GET, "/roles/{r}", null),
                Arguments.of(HttpMethod.PATCH, "/roles/{r}", Map.of("name", "Evil")),
                Arguments.of(HttpMethod.DELETE, "/roles/{r}", null),
                Arguments.of(HttpMethod.PUT, "/roles/{r}/permissions", Map.of("permissionIds", List.of())),
                Arguments.of(HttpMethod.GET, "/roles/{r}/members", null));
    }

    @ParameterizedTest(name = "{0} /tenants/'{'own'}'{1}")
    @MethodSource("victimResourcesUnderOwnTenantPath")
    void anotherTenantsResourcesAreInvisibleEvenUnderYourOwnTenantPath(HttpMethod method, String path, Object body)
            throws Exception {
        String resolved = "/api/v1/tenants/" + attacker.id() + path.replace("{self}", attacker.adminId().toString())
                .replace("{u}", victimUser.toString()).replace("{r}", victimRole.toString());

        api.request(method, attacker.adminToken(), resolved, body).andExpect(status().isNotFound());
    }

    @Test
    void listsOnlyReturnTheCallersTenantData() throws Exception {
        String own = "/api/v1/tenants/" + attacker.id();

        api.get(attacker.adminToken(), own + "/users?size=100")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", not(empty())))
                .andExpect(jsonPath("$.content[*].tenantId", everyItem(is(attacker.id().toString()))));
        api.get(attacker.adminToken(), own + "/roles?size=100")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", not(empty())))
                .andExpect(jsonPath("$.content[*].tenantId", everyItem(is(attacker.id().toString()))))
                .andExpect(jsonPath("$.content[*].name", not(hasItem("VictimRole"))));
    }

    private String victimPath(String path) {
        return "/api/v1/tenants/" + victim.id()
                + path.replace("{u}", victimUser.toString()).replace("{r}", victimRole.toString());
    }
}
