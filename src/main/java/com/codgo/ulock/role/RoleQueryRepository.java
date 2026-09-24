package com.codgo.ulock.role;

import com.codgo.ulock.common.error.InvalidRequestException;
import com.codgo.ulock.role.RoleDtos.RoleMemberResponse;
import com.codgo.ulock.role.RoleDtos.RoleSummary;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only access queries in plain SQL, returning DTOs and never entities. Each runs in one round
 * trip, and every one is scoped by tenant.
 * <p>{@link #hasPermission} is the {@code /authz/check} hot path (p95 &lt; 50 ms). It is a single
 * {@code EXISTS} over primary-key and unique-index lookups. These are read models, so they may join
 * the {@code users} and {@code tenants} tables at SQL level. All writes to those tables go through
 * their own slices.
 */
@Repository
class RoleQueryRepository {

    /** Grants that are in effect: held by an ACTIVE user of an ACTIVE tenant through the tenant's own roles. */
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

    /** API sort properties of the role-members list, mapped to columns. Anything else is rejected. */
    private static final Map<String, String> MEMBER_SORT_COLUMNS = Map.of(
            "email", "u.email", "fullName", "u.full_name", "status", "u.status", "id", "u.id", "userId", "u.id");

    private final NamedParameterJdbcTemplate jdbc;

    RoleQueryRepository(NamedParameterJdbcTemplate jdbc) {
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

    /** System permissions the user's roles grant, whatever the user's status. Used by the privilege guard. */
    Set<String> grantedSystemPermissionCodes(UUID tenantId, UUID userId) {
        return new HashSet<>(jdbc.queryForList("""
                        select distinct p.code
                        from user_roles ur
                        join roles r on r.id = ur.role_id and r.tenant_id = :tenantId
                        join role_permissions rp on rp.role_id = r.id
                        join permissions p on p.id = rp.permission_id and p.tenant_id is null
                        where ur.user_id = :userId
                        """,
                Map.of("tenantId", tenantId, "userId", userId), String.class));
    }

    List<RoleSummary> rolesOfUser(UUID tenantId, UUID userId) {
        return jdbc.query("""
                        select r.id, r.name
                        from user_roles ur
                        join roles r on r.id = ur.role_id and r.tenant_id = :tenantId
                        where ur.user_id = :userId
                        order by r.name
                        """,
                Map.of("tenantId", tenantId, "userId", userId),
                (rs, i) -> new RoleSummary(rs.getObject("id", UUID.class), rs.getString("name")));
    }

    Page<RoleMemberResponse> membersOfRole(UUID tenantId, UUID roleId, Pageable pageable) {
        String from = """
                from user_roles ur
                join roles r on r.id = ur.role_id and r.tenant_id = :tenantId
                join users u on u.id = ur.user_id and u.tenant_id = :tenantId
                where r.id = :roleId
                """;
        var params = new MapSqlParameterSource(Map.of("tenantId", tenantId, "roleId", roleId,
                "limit", pageable.getPageSize(), "offset", pageable.getOffset()));
        Long total = jdbc.queryForObject("select count(*) " + from, params, Long.class);
        List<RoleMemberResponse> content = jdbc.query(
                "select u.id, u.email, u.full_name, u.status " + from + orderBy(pageable.getSort())
                        + " limit :limit offset :offset",
                params,
                (rs, i) -> new RoleMemberResponse(rs.getObject("id", UUID.class), rs.getString("email"),
                        rs.getString("full_name"), rs.getString("status")));
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private static String orderBy(Sort sort) {
        if (sort.isUnsorted()) {
            return " order by u.email, u.id";
        }
        return sort.stream()
                .map(order -> {
                    String column = MEMBER_SORT_COLUMNS.get(order.getProperty());
                    if (column == null) {
                        throw new InvalidRequestException("Unknown sort property: " + order.getProperty());
                    }
                    return column + (order.isAscending() ? " asc" : " desc");
                })
                .collect(Collectors.joining(", ", " order by ", ""));
    }
}
