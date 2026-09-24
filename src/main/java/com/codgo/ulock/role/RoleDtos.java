package com.codgo.ulock.role;

import static com.codgo.ulock.common.web.ValidationPatterns.NOT_BLANK;

import com.codgo.ulock.user.application.port.in.model.UserView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RoleDtos {

    private RoleDtos() {}

    public record CreateRoleRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description) {}

    /** Partial update: null fields are left unchanged. */
    public record UpdateRoleRequest(
            @Size(min = 1, max = 100) @Pattern(regexp = NOT_BLANK, message = "must not be blank") String name,
            @Size(max = 500) String description) {}

    public record ReplacePermissionsRequest(@NotNull @Size(max = 500) Set<@NotNull UUID> permissionIds) {}

    public record CreatePermissionRequest(
            @NotBlank
            @Size(max = 100)
            @Pattern(regexp = "^[a-z][a-z0-9_-]*(:[a-z][a-z0-9_-]*)+$",
                    message = "must look like 'resource:action', e.g. invoice:approve")
            String code,
            @Size(max = 500) String description) {}

    public record PermissionResponse(UUID id, String code, String description, boolean system) {

        static PermissionResponse from(Permission permission) {
            return new PermissionResponse(permission.getId(), permission.getCode(), permission.getDescription(),
                    permission.isSystem());
        }
    }

    public record RoleResponse(
            UUID id,
            UUID tenantId,
            String name,
            String description,
            boolean reserved,
            List<PermissionResponse> permissions,
            Instant createdAt,
            Instant updatedAt) {

        static RoleResponse from(Role role) {
            List<PermissionResponse> permissions = role.getPermissions().stream()
                    .sorted(Comparator.comparing(Permission::getCode))
                    .map(PermissionResponse::from)
                    .toList();
            return new RoleResponse(role.getId(), role.getTenantId(), role.getName(), role.getDescription(),
                    role.isReserved(), permissions, role.getCreatedAt(), role.getUpdatedAt());
        }
    }

    public record RoleMemberResponse(UUID userId, String email, String fullName, String status) {

        static RoleMemberResponse from(UserView user) {
            return new RoleMemberResponse(user.id().value(), user.email().value(), user.fullName(), user.status());
        }
    }

    public record RoleSummary(UUID id, String name) {}

    /**
     * A user's roles and the permissions in effect, exactly as the authorization check evaluates
     * them: empty while the user is INACTIVE or the tenant SUSPENDED.
     */
    public record AccessSummaryResponse(
            UUID userId,
            String email,
            String status,
            List<RoleSummary> roles,
            List<String> effectivePermissions) {}
}
