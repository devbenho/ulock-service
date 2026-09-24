package com.codgo.ulock.user.api;

import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.user.api.AuthenticationResult;

/** Verifies a user's password and applies the lockout rules. */
public interface AuthenticateUserUseCase {

    /** Never throws for bad credentials; failures come back as a result so their side effects commit. */
    AuthenticationResult authenticate(TenantId tenantId, String email, String password);
}
