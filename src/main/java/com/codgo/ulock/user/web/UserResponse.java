package com.codgo.ulock.user.web;

import com.codgo.ulock.user.api.UserView;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        UUID tenantId,
        String email,
        String fullName,
        String status,
        boolean locked,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(UserView user, Instant now) {
        return new UserResponse(user.id().value(), user.tenantId().value(), user.email().value(), user.fullName(),
                user.status(), user.lockedAt(now), user.lastLoginAt(), user.createdAt(), user.updatedAt());
    }
}
