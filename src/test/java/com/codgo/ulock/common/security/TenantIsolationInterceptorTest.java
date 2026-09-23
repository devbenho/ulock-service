package com.codgo.ulock.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.codgo.ulock.common.error.ForbiddenException;
import com.codgo.ulock.common.error.InvalidRequestException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerMapping;

class TenantIsolationInterceptorTest {

    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final TenantIsolationInterceptor interceptor = new TenantIsolationInterceptor(new TenantAccess(), events);
    private final UUID ownTenant = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsTheCallersOwnTenant() {
        authenticate();

        assertThat(interceptor.preHandle(request(ownTenant.toString()), new MockHttpServletResponse(), new Object())).isTrue();
        verify(events, never()).publishEvent(any());
    }

    @Test
    void rejectsAndPublishesAnotherTenant() {
        authenticate();
        UUID other = UUID.randomUUID();

        assertThatThrownBy(() -> interceptor.preHandle(request(other.toString()), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ForbiddenException.class);
        ArgumentCaptor<TenantAccessDeniedEvent> event = ArgumentCaptor.forClass(TenantAccessDeniedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().requestedTenantId()).isEqualTo(other);
        assertThat(event.getValue().actor().userId()).isEqualTo(userId);
    }

    @Test
    void failsClosedOnMalformedTenantIds() {
        authenticate();

        assertThatThrownBy(() -> interceptor.preHandle(request("not-a-uuid"), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void parsesPaddedIdsExactlyAsTheArgumentBinderDoes() {
        authenticate();

        assertThat(interceptor.preHandle(request(" " + ownTenant + "\t"), new MockHttpServletResponse(), new Object()))
                .isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(request(" " + UUID.randomUUID()), new MockHttpServletResponse(),
                new Object())).isInstanceOf(ForbiddenException.class);
    }

    private void authenticate() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(userId.toString())
                .claim(TokenClaims.TENANT_ID, ownTenant.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static MockHttpServletRequest request(String tenantId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tenants/" + tenantId + "/users");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("tenantId", tenantId));
        return request;
    }
}
