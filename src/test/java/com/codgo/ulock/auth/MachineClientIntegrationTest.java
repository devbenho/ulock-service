package com.codgo.ulock.auth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codgo.ulock.support.Api.ClientFixture;
import com.codgo.ulock.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MachineClientIntegrationTest extends IntegrationTest {

    @Test
    void clientCredentialsGrantReturnsAStandardOAuthTokenResponse() throws Exception {
        ClientFixture client = api.createMachineClient();

        api.clientTokenRequest(client.clientId(), client.clientSecret())
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andExpect(jsonPath("$.scope").value("authz:check"));
    }

    @Test
    void wrongSecretsAndUnknownClientsAreRejected() throws Exception {
        ClientFixture client = api.createMachineClient();

        api.clientTokenRequest(client.clientId(), "wrong-secret").andExpect(status().isUnauthorized());
        api.clientTokenRequest("ulk_unknown", client.clientSecret()).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyTheClientCredentialsGrantIsSupported() throws Exception {
        ClientFixture client = api.createMachineClient();

        api.tokenRequest("password", client.clientId(), client.clientSecret()).andExpect(status().isBadRequest());
    }

    @Test
    void clientSecretsInTheQueryStringAreRejected() throws Exception {
        ClientFixture client = api.createMachineClient();

        api.post(null, "/api/v1/auth/token?client_secret=" + client.clientSecret(), null)
                .andExpect(status().is4xxClientError());
        api.tokenRequestWithSecretInQuery(client.clientId(), client.clientSecret())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("client_secret must be sent in the request body"));
        api.tokenRequestWithSecretInQuery(client.clientId(), client.clientSecret(), "client%5Fsecret")
                .andExpect(status().isBadRequest());
    }

    @Test
    void secretIsShownOnlyAtCreation() throws Exception {
        ClientFixture client = api.createMachineClient();

        api.get(api.platformAdminToken(), "/api/v1/machine-clients?sort=createdAt,desc&size=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].clientId").value(client.clientId()))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].clientSecret").doesNotExist());
    }

    @Test
    void disablingAClientRevokesTokenIssuanceAndExistingTokens() throws Exception {
        ClientFixture client = api.createMachineClient();

        api.patch(api.platformAdminToken(), "/api/v1/machine-clients/" + client.id(), Map.of("status", "DISABLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        api.clientTokenRequest(client.clientId(), client.clientSecret()).andExpect(status().isUnauthorized());
        api.post(client.accessToken(), "/api/v1/authz/check", Map.of(
                        "tenantId", UUID.randomUUID().toString(), "userId", UUID.randomUUID().toString(), "permission", "a:b"))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientManagementIsAuditedInThePlatformTenant() throws Exception {
        ClientFixture client = api.createMachineClient();
        api.patch(api.platformAdminToken(), "/api/v1/machine-clients/" + client.id(), Map.of("name", "Renamed system"))
                .andExpect(status().isOk());

        String platformAudit = "/api/v1/tenants/00000000-0000-0000-0000-000000000001/audit-logs?size=100&action=";
        api.get(api.platformAdminToken(), platformAudit + "MACHINE_CLIENT_CREATED")
                .andExpect(jsonPath("$.content[?(@.targetId == '" + client.id() + "')].details.clientId")
                        .value(client.clientId()));
        api.get(api.platformAdminToken(), platformAudit + "MACHINE_CLIENT_UPDATED")
                .andExpect(jsonPath("$.content[?(@.targetId == '" + client.id() + "')].details.name")
                        .value("Renamed system"));
    }

    @Test
    void onlyPlatformAdminsManageClients() throws Exception {
        var tenant = api.createTenant();

        api.post(tenant.adminToken(), "/api/v1/machine-clients", Map.of("name", "sneaky")).andExpect(status().isForbidden());
        api.get(tenant.adminToken(), "/api/v1/machine-clients").andExpect(status().isForbidden());
    }

    @Test
    void updatingAnUnknownClientIsNotFound() throws Exception {
        api.patch(api.platformAdminToken(), "/api/v1/machine-clients/" + UUID.randomUUID(), Map.of("name", "x"))
                .andExpect(status().isNotFound());
    }
}
