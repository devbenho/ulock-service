package com.codgo.ulock.role;

import java.util.Arrays;
import java.util.Set;

/** uLock's own roles. They are provisioned by uLock and cannot be renamed, edited or deleted. */
public enum ReservedRole {
    /** Exists only in the platform tenant, seeded by migration with every system permission. */
    PLATFORM_ADMIN(Set.of()),
    TENANT_ADMIN(SystemPermission.tenantCodes()),
    TENANT_AUDITOR(Set.of(SystemPermission.AUDIT_READ.code()));

    private final Set<String> permissionCodes;

    ReservedRole(Set<String> permissionCodes) {
        this.permissionCodes = permissionCodes;
    }

    Set<String> permissionCodes() {
        return permissionCodes;
    }

    public static boolean isReserved(String roleName) {
        return Arrays.stream(values()).anyMatch(role -> role.name().equalsIgnoreCase(roleName));
    }
}
