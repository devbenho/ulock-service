package com.codgo.ulock.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codgo.ulock.audit.AuditAction;
import com.codgo.ulock.audit.AuditService;
import com.codgo.ulock.auth.AuthDtos.LoginRequest;
import com.codgo.ulock.common.error.AuthenticationFailedException;
import com.codgo.ulock.common.security.JwtProperties;
import com.codgo.ulock.role.AccessService;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.tenant.Tenant;
import com.codgo.ulock.tenant.TenantService;
import com.codgo.ulock.user.application.port.in.AuthenticateUserUseCase;
import com.codgo.ulock.user.application.port.in.GetUserUseCase;
import com.codgo.ulock.user.application.port.in.model.AuthenticationResult.Outcome;
import com.codgo.ulock.user.application.port.in.model.AuthenticationResult;
import com.codgo.ulock.user.application.port.in.model.UserView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");

    private final TenantService tenantService = mock(TenantService.class);
    private final AuthenticateUserUseCase authenticateUser = mock(AuthenticateUserUseCase.class);
    private final GetUserUseCase getUser = mock(GetUserUseCase.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AccessService accessService = mock(AccessService.class);
    private final AccessTokenIssuer accessTokens = mock(AccessTokenIssuer.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final AuditService audit = mock(AuditService.class);

    private final UUID tenantId = UUID.randomUUID();
    private final UserView user = new UserView(UserId.newId(), TenantId.of(tenantId), Email.of("u@acme.test"), "U",
            "ACTIVE", true);
    private AuthService service;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
        service = new AuthService(tenantService, authenticateUser, getUser, passwordEncoder, accessService,
                accessTokens, refreshTokens, audit,
                new JwtProperties("iss", Duration.ofMinutes(15), Duration.ofDays(7), "kid", "", false),
                Clock.fixed(NOW, ZoneOffset.UTC));
        tenant = mock(Tenant.class);
        when(tenant.getId()).thenReturn(tenantId);
        when(tenant.isActive()).thenReturn(true);
        when(tenantService.findBySlug("acme")).thenReturn(Optional.of(tenant));
        when(refreshTokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void theFailureThatLocksTheAccountIsAuditedAsAccountLocked() {
        Instant lockedUntil = NOW.plus(Duration.ofMinutes(15));
        when(authenticateUser.authenticate(TenantId.of(tenantId), "u@acme.test", "bad"))
                .thenReturn(new AuthenticationResult(Outcome.BAD_PASSWORD, user, lockedUntil));

        assertThatThrownBy(() -> service.login(new LoginRequest("acme", "u@acme.test", "bad")))
                .isInstanceOf(AuthenticationFailedException.class);

        UUID userId = user.id().value();
        verify(audit).recordAs(eq(userId), eq(tenantId), eq(AuditAction.LOGIN_FAILED), any(),
                eq(Map.of("email", "u@acme.test", "reason", "BAD_PASSWORD")));
        verify(audit).recordAs(eq(userId), eq(tenantId), eq(AuditAction.ACCOUNT_LOCKED), any(),
                eq(Map.of("lockedUntil", lockedUntil.toString())));
    }

    @Test
    void unknownUsersAreAuditedWithTheAttemptedEmailAndNoActor() {
        when(authenticateUser.authenticate(any(), any(), any()))
                .thenReturn(new AuthenticationResult(Outcome.UNKNOWN_USER, null, null));

        assertThatThrownBy(() -> service.login(new LoginRequest("acme", "ghost@acme.test", "pw")))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(audit).recordAs(eq(null), eq(tenantId), eq(AuditAction.LOGIN_FAILED), eq(null),
                eq(Map.of("email", "ghost@acme.test", "reason", "UNKNOWN_USER")));
    }

    @Test
    void unknownTenantsStillSpendAHashComparisonAndAreNotAudited() {
        when(tenantService.findBySlug("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nope", "u@acme.test", "pw")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("Invalid credentials");
        verify(passwordEncoder).matches("pw", "dummy-hash");
        verifyNoInteractions(audit, authenticateUser);
    }

    @Test
    void suspendedTenantsRejectLoginWithoutCheckingThePassword() {
        when(tenant.isActive()).thenReturn(false);
        when(getUser.findUserByEmail(TenantId.of(tenantId), "u@acme.test")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("acme", "u@acme.test", "right")))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(authenticateUser, never()).authenticate(any(), any(), any());
        verify(audit).recordAs(eq(user.id().value()), eq(tenantId), eq(AuditAction.LOGIN_FAILED), any(),
                eq(Map.of("email", "u@acme.test", "reason", "TENANT_SUSPENDED")));
    }

    @Test
    void successfulLoginIssuesBothTokens() {
        when(authenticateUser.authenticate(TenantId.of(tenantId), "u@acme.test", "right"))
                .thenReturn(new AuthenticationResult(Outcome.AUTHENTICATED, user, null));
        when(accessService.roleNamesOf(user.id().value())).thenReturn(List.of("TENANT_ADMIN"));
        when(accessTokens.forUser(user.id().value(), tenantId, List.of("TENANT_ADMIN"))).thenReturn("jwt");

        var response = service.login(new LoginRequest("acme", "u@acme.test", "right"));

        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.refreshToken()).hasSizeGreaterThan(40);
        assertThat(response.refreshTokenExpiresIn()).isEqualTo(Duration.ofDays(7).toSeconds());
        verify(audit).recordAs(eq(user.id().value()), eq(tenantId), eq(AuditAction.LOGIN_SUCCEEDED), any(), eq(null));
    }

    @Test
    void refreshIsRefusedOnceTheUserIsInactive() {
        RefreshToken token = new RefreshToken(user.id().value(), tenantId, "h", NOW, NOW.plusSeconds(60), null);
        when(refreshTokens.findByTokenHashForUpdate(SecureTokens.sha256("raw"))).thenReturn(Optional.of(token));
        when(getUser.findUser(TenantId.of(tenantId), user.id()))
                .thenReturn(Optional.of(new UserView(user.id(), user.tenantId(), user.email(), "U", "INACTIVE", false)));

        assertThatThrownBy(() -> service.refresh("raw")).isInstanceOf(AuthenticationFailedException.class);
        assertThat(token.isRevoked()).isFalse();
    }

    @Test
    void replayOfARotatedRefreshTokenRevokesEverySession() {
        RefreshToken rotated = new RefreshToken(user.id().value(), tenantId, "h", NOW, NOW.plusSeconds(60), null);
        rotated.rotateTo(UUID.randomUUID(), NOW);
        when(refreshTokens.findByTokenHashForUpdate(SecureTokens.sha256("raw"))).thenReturn(Optional.of(rotated));

        assertThatThrownBy(() -> service.refresh("raw")).isInstanceOf(AuthenticationFailedException.class);

        verify(refreshTokens).revokeAllActive(user.id().value(), NOW);
        verify(audit).recordAs(eq(user.id().value()), eq(tenantId), eq(AuditAction.REFRESH_TOKEN_REUSED), any(), any());
    }

    @Test
    void expiredRefreshTokensAreRejected() {
        RefreshToken expired = new RefreshToken(user.id().value(), tenantId, "h", NOW.minusSeconds(120), NOW, null);
        when(refreshTokens.findByTokenHashForUpdate(SecureTokens.sha256("raw"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.refresh("raw")).isInstanceOf(AuthenticationFailedException.class);
        verify(refreshTokens, never()).revokeAllActive(any(), any());
    }
}
