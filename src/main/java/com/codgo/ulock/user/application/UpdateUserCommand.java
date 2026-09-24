package com.codgo.ulock.user.application;

import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.domain.UserStatus;

/**
 * Partial update: null fields are left unchanged.
 *
 * @param actor the user performing the change, or null for system callers
 */
public record UpdateUserCommand(TenantId tenantId, UserId userId, String fullName, UserStatus status, UserId actor) {}
