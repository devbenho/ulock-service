package com.codgo.ulock.user.domain.model;

import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import java.time.Instant;

/** Complete persistent state of a {@link User}, used to store and rehydrate the aggregate. */
public record UserSnapshot(
        UserId id,
        TenantId tenantId,
        Email email,
        String passwordHash,
        String fullName,
        UserStatus status,
        Instant lastLoginAt,
        int failedLoginCount,
        Instant failedLoginWindowStart,
        Instant lockedUntil,
        Instant createdAt,
        Instant updatedAt) {}
