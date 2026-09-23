package com.codgo.ulock.auth;

import com.codgo.ulock.audit.AuditAction;
import com.codgo.ulock.audit.AuditService;
import com.codgo.ulock.audit.AuditTarget;
import com.codgo.ulock.auth.AuthDtos.LoginRequest;
import com.codgo.ulock.auth.AuthDtos.TokenResponse;
import com.codgo.ulock.common.error.AuthenticationFailedException;
import com.codgo.ulock.common.security.JwtProperties;
import com.codgo.ulock.common.security.SecurityProperties;
import com.codgo.ulock.common.web.ClientIp;
import com.codgo.ulock.role.AccessService;
import com.codgo.ulock.tenant.Tenant;
import com.codgo.ulock.tenant.TenantService;
import com.codgo.ulock.user.User;
import com.codgo.ulock.user.UserCredentialsRevokedEvent;
import com.codgo.ulock.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login, refresh-token rotation and logout. Failures deliberately share one generic message, and
 * their side effects (failed-login counters, audit entries, reuse revocation) are committed even
 * though the request fails.
 */
@Service
class AuthService {

    static final String INVALID_CREDENTIALS = "Invalid credentials";
    static final String INVALID_REFRESH_TOKEN = "Invalid refresh token";
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final TenantService tenantService;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AccessService accessService;
    private final AccessTokenIssuer accessTokens;
    private final RefreshTokenRepository refreshTokens;
    private final AuditService audit;
    private final SecurityProperties securityProperties;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    /** Compared against when no user matches, so response timing does not reveal whether one exists. */
    private final String dummyPasswordHash;

    AuthService(TenantService tenantService, UserRepository users, PasswordEncoder passwordEncoder,
                AccessService accessService, AccessTokenIssuer accessTokens, RefreshTokenRepository refreshTokens,
                AuditService audit, SecurityProperties securityProperties, JwtProperties jwtProperties, Clock clock) {
        this.tenantService = tenantService;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.accessService = accessService;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.securityProperties = securityProperties;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(SecureTokens.random(16));
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    TokenResponse login(LoginRequest request) {
        Instant now = clock.instant();
        Optional<Tenant> tenant = tenantService.findBySlug(request.tenantSlug());
        Optional<User> candidate = tenant.flatMap(t -> users.findForLogin(t.getId(), User.normalizeEmail(request.email())));
        if (candidate.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            tenant.ifPresent(t -> recordLoginFailure(null, t.getId(), request.email(), "UNKNOWN_USER"));
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        User user = candidate.get();
        if (!tenant.get().isActive()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            recordLoginFailure(user.getId(), user.getTenantId(), user.getEmail(), "TENANT_SUSPENDED");
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        if (!user.isActive() || user.isLocked(now)) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            recordLoginFailure(user.getId(), user.getTenantId(), user.getEmail(), user.isActive() ? "LOCKED" : "INACTIVE");
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerBadPassword(user, now);
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        user.registerSuccessfulLogin(now);
        audit.recordAs(user.getId(), user.getTenantId(), AuditAction.LOGIN_SUCCEEDED,
                AuditTarget.of(AuditTarget.USER, user.getId()), null);
        return issue(user, now).response();
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    TokenResponse refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByTokenHashForUpdate(SecureTokens.sha256(rawRefreshToken))
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_REFRESH_TOKEN));
        if (token.isRevoked()) {
            if (token.wasRotated()) {
                // Replay of a rotated token: assume it was stolen and end every session of the user.
                refreshTokens.revokeAllActive(token.getUserId(), now);
                audit.recordAs(token.getUserId(), token.getTenantId(), AuditAction.REFRESH_TOKEN_REUSED,
                        AuditTarget.of(AuditTarget.USER, token.getUserId()), null);
            }
            throw new AuthenticationFailedException(INVALID_REFRESH_TOKEN);
        }
        if (token.isExpired(now)) {
            throw new AuthenticationFailedException(INVALID_REFRESH_TOKEN);
        }
        User user = users.findById(token.getUserId())
                .filter(User::isActive)
                .filter(u -> tenantService.get(u.getTenantId()).isActive())
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_REFRESH_TOKEN));
        IssuedTokens issued = issue(user, now);
        token.rotateTo(issued.refreshTokenId(), now);
        return issued.response();
    }

    /** Idempotent: unknown or already revoked tokens are ignored. */
    @Transactional
    void logout(String rawRefreshToken) {
        refreshTokens.findByTokenHashForUpdate(SecureTokens.sha256(rawRefreshToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> {
                    token.revoke(clock.instant());
                    audit.recordAs(token.getUserId(), token.getTenantId(), AuditAction.LOGOUT,
                            AuditTarget.of(AuditTarget.USER, token.getUserId()), null);
                });
    }

    @EventListener
    @Transactional
    public void onUserCredentialsRevoked(UserCredentialsRevokedEvent event) {
        refreshTokens.revokeAllActive(event.userId(), clock.instant());
    }

    private void registerBadPassword(User user, Instant now) {
        boolean lockedNow = user.registerFailedLogin(now, securityProperties.maxFailedLogins(),
                securityProperties.failedLoginWindow(), securityProperties.lockDuration());
        recordLoginFailure(user.getId(), user.getTenantId(), user.getEmail(), "BAD_PASSWORD");
        if (lockedNow) {
            audit.recordAs(user.getId(), user.getTenantId(), AuditAction.ACCOUNT_LOCKED,
                    AuditTarget.of(AuditTarget.USER, user.getId()), Map.of("lockedUntil", user.getLockedUntil().toString()));
        }
    }

    private void recordLoginFailure(UUID userId, UUID tenantId, String email, String reason) {
        audit.recordAs(userId, tenantId, AuditAction.LOGIN_FAILED,
                userId == null ? null : AuditTarget.of(AuditTarget.USER, userId),
                Map.of("email", email, "reason", reason));
    }

    private IssuedTokens issue(User user, Instant now) {
        String rawRefreshToken = SecureTokens.random(REFRESH_TOKEN_BYTES);
        RefreshToken refreshToken = refreshTokens.save(new RefreshToken(user.getId(), user.getTenantId(),
                SecureTokens.sha256(rawRefreshToken), now, now.plus(jwtProperties.refreshTokenTtl()), ClientIp.current()));
        String accessToken = accessTokens.forUser(user.getId(), user.getTenantId(), accessService.roleNamesOf(user.getId()));
        TokenResponse response = new TokenResponse(accessToken, "Bearer", accessTokens.ttlSeconds(), rawRefreshToken,
                jwtProperties.refreshTokenTtl().toSeconds());
        return new IssuedTokens(refreshToken.getId(), response);
    }

    private record IssuedTokens(UUID refreshTokenId, TokenResponse response) {}
}
