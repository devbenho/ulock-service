package com.codgo.ulock.role;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Hot-path access queries in plain SQL. Each resolves in one round trip and only counts grants held
 * by an ACTIVE user in an ACTIVE tenant.
 */
@Repository
class AccessRepository {

    private static final String ACTIVE_GRANTS = """
            from users u
            join tenants t on t.id = u.tenant_id
            join user_roles ur on ur.user_id = u.id
            join roles r on r.id = ur.role_id and r.tenant_id = u.tenant_id
            join role_permissions rp on rp.role_id = r.id
            join permissions p on p.id = rp.permission_id and (p.tenant_id is null or p.tenant_id = u.tenant_id)
            where u.id = :userId
              and u.tenant_id = :tenantId
              and u.status = 'ACTIVE'
              and t.status = 'ACTIVE'
            """;

    private final NamedParameterJdbcTemplate jdbc;

    AccessRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    boolean hasPermission(UUID tenantId, UUID userId, String permissionCode) {
        Boolean allowed = jdbc.queryForObject(
                "select exists (select 1 " + ACTIVE_GRANTS + " and p.code = :code)",
                Map.of("tenantId", tenantId, "userId", userId, "code", permissionCode),
                Boolean.class);
        return Boolean.TRUE.equals(allowed);
    }

    List<String> effectivePermissionCodes(UUID tenantId, UUID userId) {
        return jdbc.queryForList(
                "select distinct p.code " + ACTIVE_GRANTS + " order by p.code",
                Map.of("tenantId", tenantId, "userId", userId),
                String.class);
    }
}
