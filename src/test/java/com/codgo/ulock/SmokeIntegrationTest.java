package com.codgo.ulock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codgo.ulock.role.SystemPermission;
import com.codgo.ulock.support.IntegrationTest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class SmokeIntegrationTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @Test
    void healthIsUp() throws Exception {
        api.get(null, "/actuator/health").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void jwksPublishesOnlyThePublicSigningKey() throws Exception {
        api.get(null, "/.well-known/jwks.json")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].kid").value("ulock-key-1"))
                .andExpect(jsonPath("$.keys[0].n").exists())
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }

    @Test
    void bootstrappedPlatformAdminCanLogIn() throws Exception {
        api.loginRequest("platform", "platform-admin@ulock.test", "platform-admin-password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void metricsAreRestrictedToPlatformOperators() throws Exception {
        api.get(null, "/actuator/metrics").andExpect(status().isUnauthorized());
        api.get(api.createTenant().adminToken(), "/actuator/metrics").andExpect(status().isForbidden());
        api.get(api.platformAdminToken(), "/actuator/metrics").andExpect(status().isOk());
    }

    @Test
    void corsAllowsOnlyTheAdminUiOrigin() throws Exception {
        mvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
        mvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void seededSystemPermissionsMatchTheSystemPermissionEnum() {
        List<String> seeded = jdbc.queryForList("select code from permissions where tenant_id is null", String.class);

        assertThat(seeded).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(SystemPermission.values()).map(SystemPermission::code).toList());
    }
}
