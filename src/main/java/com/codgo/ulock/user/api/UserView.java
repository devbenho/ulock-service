package com.codgo.ulock.user.api;

import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import java.time.Instant;

/**
 * Read model of a user, used both by the slice's own queries and by other slices.
 * {@code status} is {@code ACTIVE} or {@code INACTIVE}. It is a string so other slices never
 * depend on the user domain.
 */
public record UserView( 
        UserId id,
        TenantId tenantId,
        Email email,
        String fullName,
        String status,
        boolean active,
        Instant lockedUntil,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {

    public boolean lockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
