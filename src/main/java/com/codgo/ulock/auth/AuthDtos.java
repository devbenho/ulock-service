package com.codgo.ulock.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

final class AuthDtos {

    private AuthDtos() {}

    record LoginRequest(@NotBlank String tenantSlug, @NotBlank String email, @NotBlank String password) {}

    record RefreshRequest(@NotBlank String refreshToken) {}

    record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
                         long refreshTokenExpiresIn) {}

    /** RFC 6749 section 5.1 token response, hence snake_case. */
    record ClientTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("scope") String scope) {}

    record AuthzCheckRequest(@NotNull UUID tenantId, @NotNull UUID userId, @NotBlank String permission) {}

    enum Decision { ALLOW, DENY }

    record AuthzCheckResponse(Decision decision) {}
}
