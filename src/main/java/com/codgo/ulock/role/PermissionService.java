package com.codgo.ulock.role;

import com.codgo.ulock.audit.AuditAction;
import com.codgo.ulock.audit.AuditService;
import com.codgo.ulock.audit.AuditTarget;
import com.codgo.ulock.common.PlatformTenant;
import com.codgo.ulock.common.error.ConflictException;
import com.codgo.ulock.common.error.InvalidRequestException;
import com.codgo.ulock.role.RoleDtos.CreatePermissionRequest;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PermissionService {

    /** A NOT IN over an empty list is not portable, so the platform excludes a code that never exists. */
    private static final Set<String> NOTHING_EXCLUDED = Set.of("");

    private final PermissionRepository permissions;
    private final AuditService audit;

    PermissionService(PermissionRepository permissions, AuditService audit) {
        this.permissions = permissions;
        this.audit = audit;
    }

    @Transactional
    Permission create(UUID tenantId, CreatePermissionRequest request) {
        String code = request.code();
        if (permissions.existsByTenantIdIsNullAndCode(code) || permissions.existsByTenantIdAndCode(tenantId, code)) {
            throw new ConflictException("Permission '" + code + "' already exists");
        }
        Permission permission = permissions.save(new Permission(tenantId, code, request.description()));
        audit.record(tenantId, AuditAction.PERMISSION_CREATED,
                AuditTarget.of(AuditTarget.PERMISSION, permission.getId()), Map.of("code", code));
        return permission;
    }

    @Transactional(readOnly = true)
    Page<Permission> list(UUID tenantId, Pageable pageable) {
        return permissions.findUsableBy(tenantId, excludedSystemCodes(tenantId), pageable);
    }

    /**
     * Resolves ids to the permissions visible to {@code tenantId}: its own and the system ones.
     * Other tenants' ids are reported as unknown so their existence is not revealed. Which of these a
     * role may hold is {@link Role#replacePermissions}' rule.
     */
    @Transactional(readOnly = true)
    List<Permission> resolveVisible(UUID tenantId, Collection<UUID> ids) {
        List<Permission> found = permissions.findAllByIdVisibleTo(ids, tenantId);
        if (found.size() != ids.size()) {
            Set<UUID> unknown = new HashSet<>(ids);
            found.forEach(p -> unknown.remove(p.getId()));
            throw new InvalidRequestException("Unknown permission ids: "
                    + unknown.stream().map(UUID::toString).sorted().collect(Collectors.joining(", ")));
        }
        return found;
    }

    private static Set<String> excludedSystemCodes(UUID tenantId) {
        return PlatformTenant.is(tenantId) ? NOTHING_EXCLUDED : SystemPermission.platformOnlyCodes();
    }
}
