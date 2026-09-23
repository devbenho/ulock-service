package com.codgo.ulock.role;

import com.codgo.ulock.audit.AuditAction;
import com.codgo.ulock.audit.AuditService;
import com.codgo.ulock.audit.AuditTarget;
import com.codgo.ulock.common.error.NotFoundException;
import com.codgo.ulock.common.security.CurrentActor;
import com.codgo.ulock.user.User;
import com.codgo.ulock.user.UserService;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleAssignmentService {

    private final UserRoleRepository userRoles;
    private final RoleService roleService;
    private final RoleRepository roles;
    private final UserService userService;
    private final AuditService audit;
    private final PrivilegeGuard privilegeGuard;
    private final Clock clock;

    RoleAssignmentService(UserRoleRepository userRoles, RoleService roleService, RoleRepository roles,
                          UserService userService, AuditService audit, PrivilegeGuard privilegeGuard, Clock clock) {
        this.userRoles = userRoles;
        this.roleService = roleService;
        this.roles = roles;
        this.userService = userService;
        this.audit = audit;
        this.privilegeGuard = privilegeGuard;
        this.clock = clock;
    }

    /** Idempotent: assigning a role the user already holds changes nothing. */
    @Transactional
    public void assign(UUID tenantId, UUID userId, UUID roleId) {
        User user = userService.get(tenantId, userId);
        Role role = roleService.find(tenantId, roleId);
        privilegeGuard.requireCanGrant(role.getPermissions());
        if (userRoles.insertIfAbsent(user.getId(), role.getId(), CurrentActor.userId(), clock.instant()) == 0) {
            return;
        }
        audit.record(tenantId, AuditAction.ROLE_ASSIGNED, AuditTarget.of(AuditTarget.USER, userId),
                Map.of("roleId", roleId.toString(), "roleName", role.getName()));
    }

    /** Assigns one of uLock's reserved roles, e.g. to a tenant's first administrator. */
    @Transactional
    public void assignReserved(UUID tenantId, UUID userId, ReservedRole reservedRole) {
        Role role = roles.findByTenantIdAndName(tenantId, reservedRole.name())
                .orElseThrow(() -> new IllegalStateException(reservedRole + " is not provisioned in " + tenantId));
        assign(tenantId, userId, role.getId());
    }

    @Transactional
    public void revoke(UUID tenantId, UUID userId, UUID roleId) {
        userService.get(tenantId, userId);
        Role role = roleService.find(tenantId, roleId);
        privilegeGuard.requireCanGrant(role.getPermissions());
        UserRoleId id = new UserRoleId(userId, roleId);
        if (!userRoles.existsById(id)) {
            throw new NotFoundException("Role assignment", roleId);
        }
        userRoles.deleteById(id);
        audit.record(tenantId, AuditAction.ROLE_REVOKED, AuditTarget.of(AuditTarget.USER, userId),
                Map.of("roleId", roleId.toString(), "roleName", role.getName()));
    }
}
