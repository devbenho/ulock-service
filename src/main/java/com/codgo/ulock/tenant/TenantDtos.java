package com.codgo.ulock.tenant;

import static com.codgo.ulock.common.web.ValidationPatterns.NOT_BLANK;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class TenantDtos {

    private TenantDtos() {}

    /** A new tenant always comes with its first TENANT_ADMIN, who then manages everything else. */
    public record CreateTenantRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank
            @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$",
                    message = "must be lowercase letters, digits and hyphens (max 63)")
            String slug,
            @NotNull @Valid InitialAdmin admin) {}

    public record InitialAdmin(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank String password) {}

    /** Partial update: null fields are left unchanged. The slug is immutable. */
    public record UpdateTenantRequest(
            @Size(min = 1, max = 200) @Pattern(regexp = NOT_BLANK, message = "must not be blank") String name,
            TenantStatus status) {}

    public record TenantResponse(UUID id, String name, String slug, TenantStatus status, Instant createdAt,
                                 Instant updatedAt) {

        static TenantResponse from(Tenant tenant) {
            return new TenantResponse(tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.getStatus(),
                    tenant.getCreatedAt(), tenant.getUpdatedAt());
        }
    }
}
