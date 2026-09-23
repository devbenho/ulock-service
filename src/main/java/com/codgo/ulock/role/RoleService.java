package com.codgo.ulock.role;

import com.codgo.ulock.audit.AuditAction;
import com.codgo.ulock.audit.AuditService;
import com.codgo.ulock.audit.AuditTarget;
import com.codgo.ulock.common.error.ConflictException;
import com.codgo.ulock.common.error.NotFoundException;
import com.codgo.ulock.role.RoleDtos.CreateRoleRequest;
import com.codgo.ulock.role.RoleDtos.RoleMemberResponse;
import com.codgo.ulock.role.RoleDtos.RoleResponse;
import com.codgo.ulock.role.RoleDtos.UpdateRoleRequest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RoleService {

    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PermissionService permissionService;
    private final PrivilegeGuard privilegeGuard;
    private final AuditService audit;

    RoleService(RoleRepository roles, UserRoleRepository userRoles, PermissionService permissionService,
                PrivilegeGuard privilegeGuard, AuditService audit) {
        this.roles = roles;
        this.userRoles = userRoles;
        this.permissionService = permissionService;
        this.privilegeGuard = privilegeGuard;
        this.audit = audit;
    }

    @Transactional
    RoleResponse create(UUID tenantId, CreateRoleRequest request) {
        String name = request.name().trim();
        requireAvailableName(tenantId, name);
        Role role = roles.save(new Role(tenantId, name, request.description()));
        audit.record(tenantId, AuditAction.ROLE_CREATED, AuditTarget.of(AuditTarget.ROLE, role.getId()),
                Map.of("name", name));
        return RoleResponse.from(role);
    }

    @Transactional(readOnly = true)
    Page<RoleResponse> list(UUID tenantId, Pageable pageable) {
        return roles.findAllByTenantId(tenantId, pageable).map(RoleResponse::from);
    }

    @Transactional(readOnly = true)
    RoleResponse get(UUID tenantId, UUID roleId) {
        return RoleResponse.from(find(tenantId, roleId));
    }

    @Transactional
    RoleResponse update(UUID tenantId, UUID roleId, UpdateRoleRequest request) {
        Role role = findEditable(tenantId, roleId);
        Map<String, Object> changes = new HashMap<>();
        if (request.name() != null && !request.name().trim().equals(role.getName())) {
            String name = request.name().trim();
            if (!name.equalsIgnoreCase(role.getName())) {
                requireAvailableName(tenantId, name);
            }
            role.rename(name);
            changes.put("name", name);
        }
        if (request.description() != null && !request.description().equals(role.getDescription())) {
            role.describe(request.description());
            changes.put("description", request.description());
        }
        if (!changes.isEmpty()) {
            audit.record(tenantId, AuditAction.ROLE_UPDATED, AuditTarget.of(AuditTarget.ROLE, roleId), changes);
            roles.flush();
        }
        return RoleResponse.from(role);
    }

    @Transactional
    void delete(UUID tenantId, UUID roleId) {
        Role role = findEditable(tenantId, roleId);
        if (userRoles.existsByIdRoleId(roleId)) {
            throw new ConflictException("Role still has members; revoke it from all users first");
        }
        roles.delete(role);
        audit.record(tenantId, AuditAction.ROLE_DELETED, AuditTarget.of(AuditTarget.ROLE, roleId),
                Map.of("name", role.getName()));
    }

    @Transactional
    RoleResponse replacePermissions(UUID tenantId, UUID roleId, Set<UUID> permissionIds) {
        Role role = findEditable(tenantId, roleId);
        List<Permission> permissions = permissionService.resolveAssignable(tenantId, permissionIds);
        Set<Permission> current = role.getPermissions();
        Set<Permission> addedOrRemoved = new HashSet<>(current);
        addedOrRemoved.addAll(permissions);
        addedOrRemoved.removeIf(p -> current.contains(p) && permissions.contains(p));
        privilegeGuard.requireCanGrant(addedOrRemoved);
        role.replacePermissions(permissions);
        List<String> codes = permissions.stream().map(Permission::getCode).sorted().toList();
        audit.record(tenantId, AuditAction.ROLE_PERMISSIONS_REPLACED, AuditTarget.of(AuditTarget.ROLE, roleId),
                Map.of("permissions", codes));
        return RoleResponse.from(role);
    }

    @Transactional(readOnly = true)
    Page<RoleMemberResponse> members(UUID tenantId, UUID roleId, Pageable pageable) {
        find(tenantId, roleId);
        return userRoles.findMembersOfRole(roleId, pageable).map(RoleMemberResponse::from);
    }

    Role find(UUID tenantId, UUID roleId) {
        return roles.findByIdAndTenantId(roleId, tenantId).orElseThrow(() -> new NotFoundException("Role", roleId));
    }

    private Role findEditable(UUID tenantId, UUID roleId) {
        Role role = find(tenantId, roleId);
        if (role.isReserved()) {
            throw new ConflictException("Role " + role.getName() + " is managed by uLock and cannot be changed");
        }
        return role;
    }

    private void requireAvailableName(UUID tenantId, String name) {
        if (ReservedRole.isReserved(name)) {
            throw new ConflictException("Role name " + name + " is reserved");
        }
        if (roles.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
            throw new ConflictException("A role named " + name + " already exists in the tenant");
        }
    }
}
