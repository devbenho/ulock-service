package com.codgo.ulock.audit;

import static com.codgo.ulock.support.Api.DEFAULT_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codgo.ulock.support.Api.TenantFixture;
import com.codgo.ulock.support.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditIntegrationTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    TenantFixture tenant;
    String auditLogs;

    @BeforeEach
    void setUp() throws Exception {
        tenant = api.createTenant();
        auditLogs = "/api/v1/tenants/" + tenant.id() + "/audit-logs";
    }

    @Test
    void recordsAdminChangesWithActorTargetAndIp() throws Exception {
        UUID userId = api.createUser(tenant, "audited@example.test");

        api.get(tenant.adminToken(), auditLogs + "?action=USER_CREATED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actorUserId").value(tenant.adminId().toString()))
                .andExpect(jsonPath("$.content[0].targetType").value("USER"))
                .andExpect(jsonPath("$.content[0].targetId").value(userId.toString()))
                .andExpect(jsonPath("$.content[0].ipAddress").value("127.0.0.1"))
                .andExpect(jsonPath("$.content[0].createdAt").exists());
    }

    @Test
    void filtersByUserAndActionNewestFirst() throws Exception {
        UUID userId = api.createUser(tenant, "actor@example.test");
        api.login(tenant.slug(), "actor@example.test", DEFAULT_PASSWORD);
        api.login(tenant.slug(), "actor@example.test", DEFAULT_PASSWORD);

        api.get(tenant.adminToken(), auditLogs + "?userId=" + userId)
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].actorUserId", everyItem(is(userId.toString()))))
                .andExpect(jsonPath("$.content[*].action", everyItem(is("LOGIN_SUCCEEDED"))));
        var logins = api.body(api.get(tenant.adminToken(), auditLogs + "?userId=" + userId)).get("content");
        assertThat(Instant.parse(logins.get(0).get("createdAt").asText()))
                .isAfterOrEqualTo(Instant.parse(logins.get(1).get("createdAt").asText()));
        api.get(tenant.adminToken(), auditLogs + "?action=USER_CREATED&userId=" + userId)
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersByTimeRange() throws Exception {
        Instant now = Instant.now();

        api.get(tenant.adminToken(), auditLogs + "?from=" + now.minus(1, ChronoUnit.HOURS) + "&to=" + now.plus(1, ChronoUnit.HOURS))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0)));
        api.get(tenant.adminToken(), auditLogs + "?from=" + now.plus(1, ChronoUnit.HOURS))
                .andExpect(jsonPath("$.totalElements").value(0));
        api.get(tenant.adminToken(), auditLogs + "?from=" + now + "&to=" + now).andExpect(status().isBadRequest());
    }

    @Test
    void paginates() throws Exception {
        api.get(tenant.adminToken(), auditLogs + "?size=1&page=1")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void tenantAuditorsCanReadLogsButNotManageUsers() throws Exception {
        UUID auditorId = api.createUser(tenant, "auditor@example.test");
        api.assignRole(tenant, auditorId, api.roleId(tenant, "TENANT_AUDITOR"));
        String token = api.login(tenant.slug(), "auditor@example.test", DEFAULT_PASSWORD).accessToken();

        api.get(token, auditLogs).andExpect(status().isOk());
        api.get(token, "/api/v1/tenants/" + tenant.id() + "/users").andExpect(status().isForbidden());
    }

    @Test
    void usersWithoutAuditReadAreForbidden() throws Exception {
        api.createUser(tenant, "nosy@example.test");
        String token = api.login(tenant.slug(), "nosy@example.test", DEFAULT_PASSWORD).accessToken();

        api.get(token, auditLogs).andExpect(status().isForbidden());
    }

    @Test
    void auditRecordsAreAppendOnlyInTheDatabase() {
        assertThatThrownBy(() -> jdbc.update("update audit_logs set action = 'TAMPERED' where tenant_id = ?", tenant.id()))
                .hasMessageContaining("audit_logs is append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_logs where tenant_id = ?", tenant.id()))
                .hasMessageContaining("audit_logs is append-only");
    }

    @Test
    void rejectsUnknownActions() throws Exception {
        api.get(tenant.adminToken(), auditLogs + "?action=NOT_AN_ACTION").andExpect(status().isBadRequest());
    }

    @Test
    void permissionCreationAndRoleRevocationAreAudited() throws Exception {
        UUID permissionId = api.createPermission(tenant, "doc:sign");
        UUID roleId = api.createRole(tenant, "Signer");
        UUID userId = api.createUser(tenant, "signer@example.test");
        api.assignRole(tenant, userId, roleId);
        api.delete(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/users/" + userId + "/roles/" + roleId)
                .andExpect(status().isNoContent());

        api.get(tenant.adminToken(), auditLogs + "?action=PERMISSION_CREATED")
                .andExpect(jsonPath("$.content[0].targetId").value(permissionId.toString()))
                .andExpect(jsonPath("$.content[0].details.code").value("doc:sign"));
        api.get(tenant.adminToken(), auditLogs + "?action=ROLE_REVOKED")
                .andExpect(jsonPath("$.content[0].targetId").value(userId.toString()))
                .andExpect(jsonPath("$.content[0].details.roleName").value("Signer"));
    }

    @Test
    void roleChangesAreAudited() throws Exception {
        UUID roleId = api.createRole(tenant, "Temp");
        api.patch(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/roles/" + roleId, Map.of("description", "d"))
                .andExpect(status().isOk());
        api.delete(tenant.adminToken(), "/api/v1/tenants/" + tenant.id() + "/roles/" + roleId)
                .andExpect(status().isNoContent());

        for (String action : new String[] {"ROLE_CREATED", "ROLE_UPDATED", "ROLE_DELETED"}) {
            api.get(tenant.adminToken(), auditLogs + "?action=" + action)
                    .andExpect(jsonPath("$.content[0].targetId").value(roleId.toString()));
        }
    }
}
