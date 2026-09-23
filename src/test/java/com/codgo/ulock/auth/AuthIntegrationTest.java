package com.codgo.ulock.auth;

import static com.codgo.ulock.support.Api.DEFAULT_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codgo.ulock.support.Api.TenantFixture;
import com.codgo.ulock.support.Api.Tokens;
import com.codgo.ulock.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AuthIntegrationTest extends IntegrationTest {

    @Autowired
    ObjectMapper json;

    TenantFixture tenant;

    @BeforeEach
    void setUp() throws Exception {
        tenant = api.createTenant();
    }

    @Test
    void loginIssuesSignedAccessTokenWithUserTenantAndRoleClaims() throws Exception {
        JsonNode body = api.body(api.loginRequest(tenant.slug(), tenant.adminEmail(), DEFAULT_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshTokenExpiresIn").value(7 * 24 * 3600)));

        String[] parts = body.get("accessToken").asText().split("\\.");
        JsonNode header = json.readTree(Base64.getUrlDecoder().decode(parts[0]));
        JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(header.get("alg").asText()).isEqualTo("RS256");
        assertThat(header.get("kid").asText()).isEqualTo("ulock-key-1");
        assertThat(claims.get("sub").asText()).isEqualTo(tenant.adminId().toString());
        assertThat(claims.get("tid").asText()).isEqualTo(tenant.id().toString());
        assertThat(claims.get("roles").get(0).asText()).isEqualTo("TENANT_ADMIN");
        assertThat(claims.get("exp").asLong() - claims.get("iat").asLong()).isEqualTo(900);
    }

    @Test
    void emailMatchingIsCaseInsensitive() throws Exception {
        api.loginRequest(tenant.slug(), tenant.adminEmail().toUpperCase(), DEFAULT_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void successfulLoginIsAuditedAndRecordsLastLogin() throws Exception {
        api.get(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/audit-logs?action=LOGIN_SUCCEEDED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actorUserId").value(tenant.adminId().toString()));
        api.get(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/users/" + tenant.adminId())
                .andExpect(jsonPath("$.lastLoginAt").exists());
    }

    @Test
    void wrongPasswordUnknownEmailAndUnknownTenantAllFailWithTheSameGenericProblem() throws Exception {
        for (var attempt : new String[][] {
                {tenant.slug(), tenant.adminEmail(), "wrong-password-123"},
                {tenant.slug(), "nobody@example.test", DEFAULT_PASSWORD},
                {"no-such-tenant", tenant.adminEmail(), DEFAULT_PASSWORD}}) {
            api.loginRequest(attempt[0], attempt[1], attempt[2])
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Invalid credentials"));
        }
    }

    @Test
    void failedLoginsAreAudited() throws Exception {
        api.loginRequest(tenant.slug(), tenant.adminEmail(), "wrong-password-123").andExpect(status().isUnauthorized());
        api.loginRequest(tenant.slug(), "ghost@example.test", "wrong-password-123").andExpect(status().isUnauthorized());

        api.get(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/audit-logs?action=LOGIN_FAILED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.details.reason == 'BAD_PASSWORD')].actorUserId")
                        .value(tenant.adminId().toString()))
                .andExpect(jsonPath("$.content[?(@.details.reason == 'UNKNOWN_USER')].details.email")
                        .value("ghost@example.test"));
    }

    @Test
    void fiveFailedLoginsLockTheAccountUntilAPasswordReset() throws Exception {
        UUID userId = api.createUser(tenant, "victim@example.test");
        for (int i = 0; i < 5; i++) {
            api.loginRequest(tenant.slug(), "victim@example.test", "wrong-password-123").andExpect(status().isUnauthorized());
        }

        api.loginRequest(tenant.slug(), "victim@example.test", DEFAULT_PASSWORD).andExpect(status().isUnauthorized());
        String usersPath = "/api/v1/tenants/" + tenant.id() + "/users/" + userId;
        api.get(tenant.adminToken(), usersPath).andExpect(jsonPath("$.locked").value(true));
        api.get(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/audit-logs?action=ACCOUNT_LOCKED")
                .andExpect(jsonPath("$.content[0].actorUserId").value(userId.toString()));

        api.post(tenant.adminToken(), usersPath + "/password-reset", Map.of("newPassword", "a-brand-new-password"))
                .andExpect(status().isNoContent());
        api.loginRequest(tenant.slug(), "victim@example.test", "a-brand-new-password").andExpect(status().isOk());
    }

    @Test
    void inactiveUsersCannotLogIn() throws Exception {
        UUID userId = api.createUser(tenant, "leaver@example.test");
        api.patch(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/users/" + userId, Map.of("status", "INACTIVE"))
                .andExpect(status().isOk());

        api.loginRequest(tenant.slug(), "leaver@example.test", DEFAULT_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void usersOfSuspendedTenantsCannotLogInOrRefresh() throws Exception {
        Tokens tokens = api.login(tenant.slug(), tenant.adminEmail(), DEFAULT_PASSWORD);
        api.patch(api.platformAdminToken(), "/api/v1/tenants/" + tenant.id(), Map.of("status", "SUSPENDED"))
                .andExpect(status().isOk());

        api.loginRequest(tenant.slug(), tenant.adminEmail(), DEFAULT_PASSWORD).andExpect(status().isUnauthorized());
        refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
        api.get(tokens.accessToken(), "/api/v1/tenants/" + tenant.id() + "/users").andExpect(status().isForbidden());

        api.patch(api.platformAdminToken(), "/api/v1/tenants/" + tenant.id(), Map.of("status", "ACTIVE"))
                .andExpect(status().isOk());
        api.get(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/audit-logs?action=LOGIN_FAILED")
                .andExpect(jsonPath("$.content[0].details.reason").value("TENANT_SUSPENDED"));
    }

    @Test
    void refreshRotatesTheRefreshToken() throws Exception {
        Tokens tokens = api.login(tenant.slug(), tenant.adminEmail(), DEFAULT_PASSWORD);

        JsonNode refreshed = api.body(refresh(tokens.refreshToken()).andExpect(status().isOk()));
        String newRefreshToken = refreshed.get("refreshToken").asText();

        assertThat(newRefreshToken).isNotEqualTo(tokens.refreshToken());
        api.get(refreshed.get("accessToken").asText(), "/api/v1/tenants/" + tenant.id()).andExpect(status().isOk());
        refresh(newRefreshToken).andExpect(status().isOk());
    }

    @Test
    void replayingARotatedRefreshTokenRevokesAllOfTheUsersSessions() throws Exception {
        Tokens tokens = api.login(tenant.slug(), tenant.adminEmail(), DEFAULT_PASSWORD);
        String rotated = api.body(refresh(tokens.refreshToken()).andExpect(status().isOk())).get("refreshToken").asText();

        refresh(tokens.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid refresh token"));

        refresh(rotated).andExpect(status().isUnauthorized());
        api.get(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/audit-logs?action=REFRESH_TOKEN_REUSED")
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void unknownRefreshTokensAreRejected() throws Exception {
        refresh("not-a-real-token").andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshTokenAndIsIdempotent() throws Exception {
        Tokens tokens = api.login(tenant.slug(), tenant.adminEmail(), DEFAULT_PASSWORD);

        logout(tokens.refreshToken()).andExpect(status().isNoContent());
        logout(tokens.refreshToken()).andExpect(status().isNoContent());

        refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
        api.get(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/audit-logs?action=LOGOUT")
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void passwordResetAndDeactivationEndExistingSessions() throws Exception {
        UUID userId = api.createUser(tenant, "session@example.test");
        String userPath = "/api/v1/tenants/" + tenant.id() + "/users/" + userId;

        Tokens beforeReset = api.login(tenant.slug(), "session@example.test", DEFAULT_PASSWORD);
        api.post(tenant.adminToken(), userPath + "/password-reset", Map.of("newPassword", "another-password-1"))
                .andExpect(status().isNoContent());
        refresh(beforeReset.refreshToken()).andExpect(status().isUnauthorized());
        api.loginRequest(tenant.slug(), "session@example.test", DEFAULT_PASSWORD).andExpect(status().isUnauthorized());

        Tokens beforeDeactivation = api.login(tenant.slug(), "session@example.test", "another-password-1");
        api.patch(tenant.adminToken(), userPath, Map.of("status", "INACTIVE")).andExpect(status().isOk());
        refresh(beforeDeactivation.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRequireAValidBearerToken() throws Exception {
        api.get(null, "/api/v1/tenants/" + tenant.id() + "/users")
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        String tampered = tenant.adminToken().substring(0, tenant.adminToken().length() - 4) + "AAAA";
        api.get(tampered, "/api/v1/tenants/" + tenant.id() + "/users").andExpect(status().isUnauthorized());
    }

    @Test
    void loginValidatesItsPayload() throws Exception {
        api.post(null, "/api/v1/auth/login", Map.of("email", "x@example.test"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("tenantSlug", "password")));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return api.post(null, "/api/v1/auth/refresh", Map.of("refreshToken", refreshToken));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return api.post(null, "/api/v1/auth/logout", Map.of("refreshToken", refreshToken));
    }
}
