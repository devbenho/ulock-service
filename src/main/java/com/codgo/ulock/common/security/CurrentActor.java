package com.codgo.ulock.common.security;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the {@link Actor} from the current security context. */
public final class CurrentActor {

    private CurrentActor() {}

    public static Optional<Actor> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        String clientId = jwt.getClaimAsString(TokenClaims.CLIENT_ID);
        if (clientId != null) {
            return Optional.of(new Actor(null, null, clientId));
        }
        String tenantId = jwt.getClaimAsString(TokenClaims.TENANT_ID);
        return Optional.of(new Actor(
                UUID.fromString(jwt.getSubject()),
                tenantId == null ? null : UUID.fromString(tenantId),
                null));
    }

    /** Authorities of the current request: permission codes for users, SCOPE_ values for clients. */
    public static Set<String> authorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    /** True when running outside any request authentication, e.g. startup bootstrap. */
    public static boolean isSystem() {
        return SecurityContextHolder.getContext().getAuthentication() == null;
    }

    /** The authenticated user's id, or null for anonymous and machine callers. */
    public static UUID userId() {
        return find().map(Actor::userId).orElse(null);
    }
}
