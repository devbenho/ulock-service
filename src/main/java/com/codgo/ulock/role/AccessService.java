package com.codgo.ulock.role;

import com.codgo.ulock.role.RoleDtos.AccessSummaryResponse;
import com.codgo.ulock.role.RoleDtos.RoleSummary;
import com.codgo.ulock.user.User;
import com.codgo.ulock.user.UserService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Answers what a user may do: permission checks, effective permissions, and access summaries. */
@Service
public class AccessService {

    private final AccessRepository accessRepository;
    private final UserRoleRepository userRoles;
    private final UserService userService;

    AccessService(AccessRepository accessRepository, UserRoleRepository userRoles, UserService userService) {
        this.accessRepository = accessRepository;
        this.userRoles = userRoles;
        this.userService = userService;
    }

    /** True only if the user and tenant are ACTIVE and one of the user's roles grants the permission. */
    public boolean hasPermission(UUID tenantId, UUID userId, String permissionCode) {
        return accessRepository.hasPermission(tenantId, userId, permissionCode);
    }

    /** Permission codes currently in effect; empty for inactive users or suspended tenants. */
    public List<String> effectivePermissionCodes(UUID tenantId, UUID userId) {
        return accessRepository.effectivePermissionCodes(tenantId, userId);
    }

    @Transactional(readOnly = true)
    public List<String> roleNamesOf(UUID userId) {
        return userRoles.findRolesOfUser(userId).stream().map(Role::getName).toList();
    }

    @Transactional(readOnly = true)
    public AccessSummaryResponse summarize(UUID tenantId, UUID userId) {
        User user = userService.get(tenantId, userId);
        List<RoleSummary> roles = userRoles.findRolesOfUser(userId).stream()
                .map(r -> new RoleSummary(r.getId(), r.getName()))
                .toList();
        return new AccessSummaryResponse(user.getId(), user.getEmail(), user.getStatus(), roles,
                effectivePermissionCodes(tenantId, userId));
    }
}
