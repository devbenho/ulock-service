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
import com.codgo.ulock.common.security.SecurityProperties;
import com.codgo.ulock.role.AccessService;
import com.codgo.ulock.tenant.Tenant;
import com.codgo.ulock.tenant.TenantService;
import com.codgo.ulock.user.User;
import com.codgo.ulock.user.UserRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");

    private final TenantService tenantService = mock(TenantService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AccessService accessService = mock(AccessService.class);
    private final AccessTokenIssuer accessTokens = mock(AccessTokenIssuer.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final AuditService audit = mock(AuditService.class);

    private AuthService service;
    private final UUID tenantId = UUID.randomUUID();
    private Tenant tenant;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
        service = new AuthService(tenantService, users, passwordEncoder, accessService, accessTokens, refreshTokens, audit,
                new SecurityProperties(12, 3, Duration.ofMinutes(15), Duration.ofMinutes(15), List.of()),
                new JwtProperties("iss", Duration.ofMinutes(15), Duration.ofDays(7), "kid", "", false),
                Clock.fixed(NOW, ZoneOffset.UTC));

        tenant = mock(Tenant.class);
        when(tenant.getId()).thenReturn(tenantId);
        when(tenant.isActive()).thenReturn(true);
        when(tenantService.findBySlug("acme")).thenReturn(Optional.of(tenant));
        user = new User(tenantId, "u@acme.test", "real-hash", "U");
        setId(user, UUID.randomUUID());
        when(users.findForLogin(tenantId, "u@acme.test")).thenReturn(Optional.of(user));
        when(refreshTokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void lockingFailureIsAuditedAsAccountLocked() {
        when(passwordEncoder.matches("bad", "real-hash")).thenReturn(false);

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.login(new LoginRequest("acme", "u@acme.test", "bad")))
                    .isInstanceOf(AuthenticationFailedException.class);
        }

        assertThat(user.isLocked(NOW)).isTrue();
        verify(audit).recordAs(eq(user.getId()), eq(tenantId), eq(AuditAction.ACCOUNT_LOCKED), any(), any());
    }

    @Test
    void aLockedAccountIsRejectedEvenWithTheRightPassword() {
        user.registerFailedLogin(NOW, 1, Duration.ofMinutes(15), Duration.ofMinutes(15));
        when(passwordEncoder.matches("right", "real-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest("acme", "u@acme.test", "right")))
                .isInstanceOf(AuthenticationFailedException.class);
        verify(passwordEncoder, never()).matches("right", "real-hash");
    }

    @Test
    void unknownTenantsStillSpendAHashComparisonAndAreNotAudited() {
        when(tenantService.findBySlug("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nope", "u@acme.test", "pw")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("Invalid credentials");
        verify(passwordEncoder).matches("pw", "dummy-hash");
        verifyNoInteractions(audit);
    }

    @Test
    void successfulLoginIssuesBothTokens() {
        when(passwordEncoder.matches("right", "real-hash")).thenReturn(true);
        when(accessService.roleNamesOf(user.getId())).thenReturn(List.of("TENANT_ADMIN"));
        when(accessTokens.forUser(user.getId(), tenantId, List.of("TENANT_ADMIN"))).thenReturn("jwt");

        var response = service.login(new LoginRequest("acme", "U@Acme.test", "right"));

        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.refreshToken()).hasSizeGreaterThan(40);
        assertThat(response.refreshTokenExpiresIn()).isEqualTo(Duration.ofDays(7).toSeconds());
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void replayOfARotatedRefreshTokenRevokesEverySession() {
        RefreshToken rotated = new RefreshToken(user.getId(), tenantId, "h", NOW, NOW.plusSeconds(60), null);
        rotated.rotateTo(UUID.randomUUID(), NOW);
        when(refreshTokens.findByTokenHashForUpdate(SecureTokens.sha256("raw"))).thenReturn(Optional.of(rotated));

        assertThatThrownBy(() -> service.refresh("raw")).isInstanceOf(AuthenticationFailedException.class);

        verify(refreshTokens).revokeAllActive(user.getId(), NOW);
        verify(audit).recordAs(eq(user.getId()), eq(tenantId), eq(AuditAction.REFRESH_TOKEN_REUSED), any(), any());
    }

    @Test
    void expiredRefreshTokensAreRejected() {
        RefreshToken expired = new RefreshToken(user.getId(), tenantId, "h", NOW.minusSeconds(120), NOW, null);
        when(refreshTokens.findByTokenHashForUpdate(SecureTokens.sha256("raw"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.refresh("raw")).isInstanceOf(AuthenticationFailedException.class);
        verify(refreshTokens, never()).revokeAllActive(any(), any());
    }

    private static void setId(User user, UUID id) throws Exception {
        Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
    }
}
