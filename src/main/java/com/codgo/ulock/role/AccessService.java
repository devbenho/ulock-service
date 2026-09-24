package com.codgo.ulock.role;

import com.codgo.ulock.role.RoleDtos.AccessSummaryResponse;
import com.codgo.ulock.role.RoleDtos.RoleSummary;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.api.UserApi;
import com.codgo.ulock.user.api.UserView;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers what a user may do: permission checks, effective permissions and access summaries. All
 * reads are projections. The database is the source of truth for authorization; the {@code roles}
 * claim in access tokens is informational and may be stale.
 */
@Service
public class AccessService {

    private final RoleQueryRepository queries;
    private final UserApi users;

    AccessService(RoleQueryRepository queries, UserApi users) {
        this.queries = queries;
        this.users = users;
    }

    /** True only if the user and tenant are ACTIVE and one of the user's roles grants the permission. */
    public boolean hasPermission(UUID tenantId, UUID userId, String permissionCode) {
        return queries.hasPermission(tenantId, userId, permissionCode);
    }

    /** Permission codes currently in effect; empty for inactive users or suspended tenants. */
    public List<String> effectivePermissionCodes(UUID tenantId, UUID userId) {
        return queries.effectivePermissionCodes(tenantId, userId);
    }

    public List<String> roleNamesOf(UUID tenantId, UUID userId) {
        return queries.rolesOfUser(tenantId, userId).stream().map(RoleSummary::name).toList();
    }

    @Transactional(readOnly = true)
    public AccessSummaryResponse summarize(UUID tenantId, UUID userId) {
        UserView user = users.getUser(TenantId.of(tenantId), UserId.of(userId));
        return new AccessSummaryResponse(userId, user.email().value(), user.status(),
                queries.rolesOfUser(tenantId, userId), queries.effectivePermissionCodes(tenantId, userId));
    }
}
