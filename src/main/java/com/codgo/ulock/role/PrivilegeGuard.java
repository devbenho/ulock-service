package com.codgo.ulock.role;

import com.codgo.ulock.common.error.ForbiddenException;
import com.codgo.ulock.common.security.CurrentActor;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Stops privilege escalation through uLock's own API. A caller may only grant, revoke or administer
 * system permissions they hold themselves, so e.g. a helpdesk role with {@code user:write} cannot
 * reset a TENANT_ADMIN's password, and a {@code role:write} holder cannot hand out TENANT_ADMIN.
 * Tenant-defined permissions are business permissions and are not restricted this way.
 */
@Component
public class PrivilegeGuard {

    private final UserRoleRepository userRoles;

    PrivilegeGuard(UserRoleRepository userRoles) {
        this.userRoles = userRoles;
    }

    void requireCanGrant(Collection<Permission> permissions) {
        requireHeld(systemCodes(permissions), "You cannot grant or revoke permissions you do not hold");
    }

    /** The user slice calls this before deactivating a user or resetting their password. */
    public void requireCanAdminister(UUID tenantId, UUID userId) {
        Set<String> targetPermissions = systemCodes(userRoles.findRolesOfUser(userId).stream()
                .flatMap(role -> role.getPermissions().stream())
                .toList());
        requireHeld(targetPermissions, "You cannot manage a user who holds permissions you do not hold");
    }

    private static void requireHeld(Set<String> required, String detail) {
        // Startup bootstrap runs without any authentication; every HTTP path is authenticated.
        if (CurrentActor.isSystem()) {
            return;
        }
        if (!CurrentActor.authorities().containsAll(required)) {
            throw new ForbiddenException(detail);
        }
    }

    private static Set<String> systemCodes(Collection<Permission> permissions) {
        return permissions.stream().filter(Permission::isSystem).map(Permission::getCode).collect(Collectors.toSet());
    }
}
