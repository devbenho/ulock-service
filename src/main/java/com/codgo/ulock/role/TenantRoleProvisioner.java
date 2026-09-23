package com.codgo.ulock.role;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates the reserved roles every tenant starts with. */
@Service
public class TenantRoleProvisioner {

    private static final List<ReservedRole> TENANT_ROLES = List.of(ReservedRole.TENANT_ADMIN, ReservedRole.TENANT_AUDITOR);

    private final RoleRepository roles;
    private final PermissionRepository permissions;

    TenantRoleProvisioner(RoleRepository roles, PermissionRepository permissions) {
        this.roles = roles;
        this.permissions = permissions;
    }

    @Transactional
    public void provision(UUID tenantId) {
        for (ReservedRole reserved : TENANT_ROLES) {
            Role role = new Role(tenantId, reserved.name(), "Built-in uLock role");
            role.replacePermissions(permissions.findAllByTenantIdIsNullAndCodeIn(reserved.permissionCodes()));
            roles.save(role);
        }
    }
}
