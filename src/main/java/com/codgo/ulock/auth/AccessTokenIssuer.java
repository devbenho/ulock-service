package com.codgo.ulock.auth;

import com.codgo.ulock.common.security.JwtProperties;
import com.codgo.ulock.common.security.TokenClaims;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Signs RS256 access tokens for users and machine clients.
 * <p>A user token carries identity and tenant context: {@code sub} and {@code tid}. Its {@code roles}
 * claim is informational only. It is fixed when the token is issued and can be up to 15 minutes stale,
 * so nothing may authorize on it. uLock resolves permissions from the database on every request, and
 * other services must call {@code /authz/check}.
 */
@Component
class AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    AccessTokenIssuer(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    String forUser(UUID userId, UUID tenantId, List<String> roleNames) {
        return sign(claims(userId.toString())
                .claim(TokenClaims.TENANT_ID, tenantId.toString())
                .claim(TokenClaims.ROLES, roleNames)
                .build());
    }

    String forClient(String clientId) {
        return sign(claims(clientId)
                .claim(TokenClaims.CLIENT_ID, clientId)
                .claim(TokenClaims.SCOPE, TokenClaims.AUTHZ_CHECK_SCOPE)
                .build());
    }

    long ttlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    private JwtClaimsSet.Builder claims(String subject) {
        Instant now = clock.instant();
        return JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()));
    }

    private String sign(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.keyId()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
