package com.codgo.ulock.user.application.port.out.security;

import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;

/** Decides whether the current caller may deactivate a user or reset their password. */
public interface AdministrationPolicyPort {

    /** Throws if the target user holds permissions the caller does not. */
    void requireCanAdminister(TenantId tenantId, UserId userId);
}
