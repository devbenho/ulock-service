package com.codgo.ulock.auth;

import com.codgo.ulock.common.security.TokenClaims;
import com.codgo.ulock.role.AccessService;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Turns a verified uLock JWT into an authentication whose authorities are resolved from the
 * database on each request, so revocations, deactivations and suspensions apply immediately rather
 * than when the token expires.
 * <ul>
 *   <li>User tokens: the user's effective permission codes, e.g. {@code user:write}.</li>
 *   <li>Machine-client tokens: {@code SCOPE_}-prefixed scopes, while the client is ACTIVE.</li>
 * </ul>
 */
@Component
class UlockJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AccessService accessService;
    private final MachineClientService machineClients;

    UlockJwtAuthenticationConverter(AccessService accessService, MachineClientService machineClients) {
        this.accessService = accessService;
        this.machineClients = machineClients;
    }

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        String clientId = jwt.getClaimAsString(TokenClaims.CLIENT_ID);
        List<GrantedAuthority> authorities = clientId != null ? clientAuthorities(jwt, clientId) : userAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private List<GrantedAuthority> clientAuthorities(Jwt jwt, String clientId) {
        String scope = jwt.getClaimAsString(TokenClaims.SCOPE);
        if (scope == null || !machineClients.isActive(clientId)) {
            return List.of();
        }
        return Arrays.stream(scope.split(" "))
                .<GrantedAuthority>map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                .toList();
    }

    private List<GrantedAuthority> userAuthorities(Jwt jwt) {
        String tenantId = jwt.getClaimAsString(TokenClaims.TENANT_ID);
        if (tenantId == null) {
            return List.of();
        }
        return accessService.effectivePermissionCodes(UUID.fromString(tenantId), UUID.fromString(jwt.getSubject()))
                .stream()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }
}
