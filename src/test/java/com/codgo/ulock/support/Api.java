package com.codgo.ulock.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Thin HTTP client over MockMvc plus fixtures that build state through the public API. */
@TestComponent
public class Api {

    public static final String PLATFORM_SLUG = "platform";
    public static final String PLATFORM_ADMIN_EMAIL = "platform-admin@ulock.test";
    public static final String PLATFORM_ADMIN_PASSWORD = "platform-admin-password";
    public static final String DEFAULT_PASSWORD = "correct-horse-battery";

    private final MockMvc mvc;
    private final ObjectMapper json;

    public Api(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    public record Tokens(String accessToken, String refreshToken) {}

    public record TenantFixture(UUID id, String slug, UUID adminId, String adminEmail, String adminToken) {}

    public record ClientFixture(UUID id, String clientId, String clientSecret, String accessToken) {}

    // --- raw requests -----------------------------------------------------------------------

    public ResultActions request(HttpMethod method, String token, String path, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(method, path);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(builder);
    }

    /** Sends the URI exactly as given, without template expansion or re-encoding. */
    public ResultActions requestRaw(HttpMethod method, String token, String encodedUri, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(method, URI.create(encodedUri));
        builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(builder);
    }

    public ResultActions get(String token, String path) throws Exception {
        return request(HttpMethod.GET, token, path, null);
    }

    public ResultActions post(String token, String path, Object body) throws Exception {
        return request(HttpMethod.POST, token, path, body);
    }

    public ResultActions put(String token, String path, Object body) throws Exception {
        return request(HttpMethod.PUT, token, path, body);
    }

    public ResultActions patch(String token, String path, Object body) throws Exception {
        return request(HttpMethod.PATCH, token, path, body);
    }

    public ResultActions delete(String token, String path) throws Exception {
        return request(HttpMethod.DELETE, token, path, null);
    }

    public JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    // --- authentication ---------------------------------------------------------------------

    public ResultActions loginRequest(String tenantSlug, String email, String password) throws Exception {
        return post(null, "/api/v1/auth/login", Map.of("tenantSlug", tenantSlug, "email", email, "password", password));
    }

    public Tokens login(String tenantSlug, String email, String password) throws Exception {
        JsonNode body = body(loginRequest(tenantSlug, email, password).andExpect(status().isOk()));
        return new Tokens(body.get("accessToken").asText(), body.get("refreshToken").asText());
    }

    public String platformAdminToken() throws Exception {
        return login(PLATFORM_SLUG, PLATFORM_ADMIN_EMAIL, PLATFORM_ADMIN_PASSWORD).accessToken();
    }

    // --- fixtures ---------------------------------------------------------------------------

    public static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** A fresh tenant with its first admin, logged in. */
    public TenantFixture createTenant() throws Exception {
        String slug = unique("t");
        String adminEmail = "admin@" + slug + ".test";
        JsonNode tenant = body(post(platformAdminToken(), "/api/v1/tenants", Map.of(
                "name", "Tenant " + slug,
                "slug", slug,
                "admin", Map.of("email", adminEmail, "fullName", "Tenant Admin", "password", DEFAULT_PASSWORD)))
                .andExpect(status().isCreated()));
        UUID tenantId = UUID.fromString(tenant.get("id").asText());
        String adminToken = login(slug, adminEmail, DEFAULT_PASSWORD).accessToken();
        JsonNode users = body(get(adminToken, "/api/v1/tenants/" + tenantId + "/users").andExpect(status().isOk()));
        UUID adminId = UUID.fromString(users.get("content").get(0).get("id").asText());
        return new TenantFixture(tenantId, slug, adminId, adminEmail, adminToken);
    }

    public UUID createUser(TenantFixture tenant, String email) throws Exception {
        JsonNode user = body(post(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/users",
                Map.of("email", email, "fullName", "User " + email, "password", DEFAULT_PASSWORD))
                .andExpect(status().isCreated()));
        return UUID.fromString(user.get("id").asText());
    }

    public UUID createPermission(TenantFixture tenant, String code) throws Exception {
        JsonNode permission = body(post(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/permissions",
                Map.of("code", code, "description", "Test permission")).andExpect(status().isCreated()));
        return UUID.fromString(permission.get("id").asText());
    }

    public UUID createRole(TenantFixture tenant, String name, UUID... permissionIds) throws Exception {
        String base = "/api/v1/tenants/" + tenant.id() + "/roles";
        JsonNode role = body(post(tenant.adminToken(), base, Map.of("name", name)).andExpect(status().isCreated()));
        UUID roleId = UUID.fromString(role.get("id").asText());
        if (permissionIds.length > 0) {
            put(tenant.adminToken(), base + "/" + roleId + "/permissions", Map.of("permissionIds", List.of(permissionIds)))
                    .andExpect(status().isOk());
        }
        return roleId;
    }

    public void assignRole(TenantFixture tenant, UUID userId, UUID roleId) throws Exception {
        post(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/users/" + userId + "/roles/" + roleId, null)
                .andExpect(status().isNoContent());
    }

    /** The id of a reserved or custom role, looked up by name. */
    public UUID roleId(TenantFixture tenant, String name) throws Exception {
        JsonNode roles = body(get(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/roles?size=100")
                .andExpect(status().isOk()));
        for (JsonNode role : roles.get("content")) {
            if (role.get("name").asText().equals(name)) {
                return UUID.fromString(role.get("id").asText());
            }
        }
        throw new AssertionError("No role named " + name);
    }

    public ResultActions clientTokenRequest(String clientId, String clientSecret) throws Exception {
        return tokenRequest("client_credentials", clientId, clientSecret);
    }

    public ResultActions tokenRequest(String grantType, String clientId, String clientSecret) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", grantType)
                .param("client_id", clientId)
                .param("client_secret", clientSecret));
    }

    public ResultActions tokenRequestWithSecretInQuery(String clientId, String clientSecret) throws Exception {
        return tokenRequestWithSecretInQuery(clientId, clientSecret, "client_secret");
    }

    public ResultActions tokenRequestWithSecretInQuery(String clientId, String clientSecret, String encodedName)
            throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(URI.create("/api/v1/auth/token?" + encodedName + "=" + clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("grant_type=client_credentials&client_id=" + clientId));
    }

    public ClientFixture createMachineClient() throws Exception {
        JsonNode created = body(post(platformAdminToken(), "/api/v1/machine-clients", Map.of("name", unique("system")))
                .andExpect(status().isCreated()));
        String clientId = created.get("clientId").asText();
        String secret = created.get("clientSecret").asText();
        String token = body(clientTokenRequest(clientId, secret).andExpect(status().isOk())).get("access_token").asText();
        return new ClientFixture(UUID.fromString(created.get("id").asText()), clientId, secret, token);
    }
}
