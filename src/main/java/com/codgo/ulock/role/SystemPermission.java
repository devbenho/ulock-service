package com.codgo.ulock.role;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Built-in permissions (seeded by {@code V2__seed_system_data.sql}) that guard uLock's own API.
 * Platform-only permissions may never be granted to roles outside the platform tenant.
 * <p>Adding one needs a migration that inserts it and back-grants it to the existing PLATFORM_ADMIN
 * and, if tenant-level, every existing TENANT_ADMIN role: grants are otherwise only made when the
 * seed runs or a tenant is created.
 */
public enum SystemPermission {
    TENANT_READ("tenant:read", true),
    TENANT_WRITE("tenant:write", true),
    CLIENT_MANAGE("client:manage", true),
    USER_READ("user:read", false),
    USER_WRITE("user:write", false),
    ROLE_READ("role:read", false),
    ROLE_WRITE("role:write", false),
    AUDIT_READ("audit:read", false);

    private final String code;
    private final boolean platformOnly;

    SystemPermission(String code, boolean platformOnly) {
        this.code = code;
        this.platformOnly = platformOnly;
    }

    public String code() {
        return code;
    }

    public boolean platformOnly() {
        return platformOnly;
    }

    public static Set<String> platformOnlyCodes() {
        return Arrays.stream(values()).filter(SystemPermission::platformOnly)
                .map(SystemPermission::code).collect(Collectors.toUnmodifiableSet());
    }

    public static Set<String> tenantCodes() {
        return Arrays.stream(values()).filter(p -> !p.platformOnly())
                .map(SystemPermission::code).collect(Collectors.toUnmodifiableSet());
    }
}
