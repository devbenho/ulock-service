package com.codgo.ulock.user;

import java.util.UUID;

/**
 * Decides whether the current caller may take over-the-shoulder actions on a user (deactivate,
 * reset password). Implemented by the role slice, which knows what each user can do.
 */
public interface UserAdministrationPolicy {

    /** @throws com.codgo.ulock.common.error.ForbiddenException if the user out-ranks the caller */
    void requireCanAdminister(UUID tenantId, UUID userId);
}
