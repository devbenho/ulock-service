package com.codgo.ulock.user;

import static com.codgo.ulock.common.web.ValidationPatterns.NOT_BLANK;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {}

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank String password) {}

    /** Partial update: null fields are left unchanged. */
    public record UpdateUserRequest(
            @Size(min = 1, max = 200) @Pattern(regexp = NOT_BLANK, message = "must not be blank") String fullName,
            UserStatus status) {}

    public record ResetPasswordRequest(@NotBlank String newPassword) {}

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

        static UserResponse from(User user, Instant now) {
            return new UserResponse(user.getId(), user.getTenantId(), user.getEmail(), user.getFullName(),
                    user.getStatus(), user.isLocked(now), user.getLastLoginAt(), user.getCreatedAt(),
                    user.getUpdatedAt());
        }
    }
}
