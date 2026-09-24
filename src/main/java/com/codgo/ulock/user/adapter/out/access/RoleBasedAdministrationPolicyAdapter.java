package com.codgo.ulock.user.adapter.out.access;

import com.codgo.ulock.role.PrivilegeGuard;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.application.port.out.security.AdministrationPolicyPort;
import org.springframework.stereotype.Component;

/** Delegates to the role slice, which knows which permissions the caller and the target user hold. */
@Component
class RoleBasedAdministrationPolicyAdapter implements AdministrationPolicyPort {

    private final PrivilegeGuard privilegeGuard;

    RoleBasedAdministrationPolicyAdapter(PrivilegeGuard privilegeGuard) {
        this.privilegeGuard = privilegeGuard;
    }

    @Override
    public void requireCanAdminister(TenantId tenantId, UserId userId) {
        privilegeGuard.requireCanAdminister(tenantId.value(), userId.value());
    }
}
