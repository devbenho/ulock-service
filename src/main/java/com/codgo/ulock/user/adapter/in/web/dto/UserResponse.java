package com.codgo.ulock.user.adapter.in.web.dto;

import com.codgo.ulock.user.domain.model.User;
import com.codgo.ulock.user.domain.model.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        UUID tenantId,
        String email,
        String fullName,
        UserStatus status,
        boolean locked,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(User user, Instant now) {
        return new UserResponse(user.id().value(), user.tenantId().value(), user.email().value(), user.fullName(),
                user.status(), user.isLocked(now), user.lastLoginAt(), user.createdAt(), user.updatedAt());
    }
}
