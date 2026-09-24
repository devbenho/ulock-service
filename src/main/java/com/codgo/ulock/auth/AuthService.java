package com.codgo.ulock.auth;

import com.codgo.ulock.audit.AuditAction;
import com.codgo.ulock.audit.AuditService;
import com.codgo.ulock.audit.AuditTarget;
import com.codgo.ulock.auth.AuthDtos.LoginRequest;
import com.codgo.ulock.auth.AuthDtos.TokenResponse;
import com.codgo.ulock.common.error.AuthenticationFailedException;
import com.codgo.ulock.common.security.JwtProperties;
import com.codgo.ulock.common.web.ClientIp;
import com.codgo.ulock.role.AccessService;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.tenant.Tenant;
import com.codgo.ulock.tenant.TenantService;
import com.codgo.ulock.user.application.port.in.AuthenticateUserUseCase;
import com.codgo.ulock.user.application.port.in.GetUserUseCase;
import com.codgo.ulock.user.application.port.in.event.UserCredentialsRevokedEvent;
import com.codgo.ulock.user.application.port.in.model.AuthenticationResult;
import com.codgo.ulock.user.application.port.in.model.UserView;
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
    private final AuthenticateUserUseCase authenticateUser;
    private final GetUserUseCase getUser;
    private final PasswordEncoder passwordEncoder;
    private final AccessService accessService;
    private final AccessTokenIssuer accessTokens;
    private final RefreshTokenRepository refreshTokens;
    private final AuditService audit;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    /** Compared against when no tenant matches, so response timing does not reveal whether one exists. */
    private final String dummyPasswordHash;

    AuthService(TenantService tenantService, AuthenticateUserUseCase authenticateUser, GetUserUseCase getUser,
                PasswordEncoder passwordEncoder, AccessService accessService, AccessTokenIssuer accessTokens,
                RefreshTokenRepository refreshTokens, AuditService audit, JwtProperties jwtProperties, Clock clock) {
        this.tenantService = tenantService;
        this.authenticateUser = authenticateUser;
        this.getUser = getUser;
        this.passwordEncoder = passwordEncoder;
        this.accessService = accessService;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(SecureTokens.random(16));
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    TokenResponse login(LoginRequest request) {
        Optional<Tenant> tenant = tenantService.findBySlug(request.tenantSlug());
        if (tenant.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        TenantId tenantId = TenantId.of(tenant.get().getId());
        if (!tenant.get().isActive()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            Optional<UserView> user = getUser.findUserByEmail(tenantId, request.email());
            recordLoginFailure(user.orElse(null), tenantId, request.email(),
                    user.isPresent() ? "TENANT_SUSPENDED" : "UNKNOWN_USER");
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        AuthenticationResult result = authenticateUser.authenticate(tenantId, request.email(), request.password());
        if (!result.authenticated()) {
            recordLoginFailure(result.user(), tenantId, request.email(), result.outcome().name());
            if (result.lockedNow()) {
                UUID userId = result.user().id().value();
                audit.recordAs(userId, tenantId.value(), AuditAction.ACCOUNT_LOCKED,
                        AuditTarget.of(AuditTarget.USER, userId), Map.of("lockedUntil", result.lockedUntil().toString()));
            }
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        UUID userId = result.user().id().value();
        audit.recordAs(userId, tenantId.value(), AuditAction.LOGIN_SUCCEEDED, AuditTarget.of(AuditTarget.USER, userId),
                null);
        return issue(userId, tenantId.value(), clock.instant()).response();
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
        boolean sessionStillValid = getUser.findUser(TenantId.of(token.getTenantId()), UserId.of(token.getUserId()))
                .filter(UserView::active)
                .filter(u -> tenantService.get(token.getTenantId()).isActive())
                .isPresent();
        if (!sessionStillValid) {
            throw new AuthenticationFailedException(INVALID_REFRESH_TOKEN);
        }
        IssuedTokens issued = issue(token.getUserId(), token.getTenantId(), now);
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
        refreshTokens.revokeAllActive(event.userId().value(), clock.instant());
    }

    /** Known users are recorded as actor and target, with their stored email; unknown ones by the attempted email. */
    private void recordLoginFailure(UserView user, TenantId tenantId, String attemptedEmail, String reason) {
        UUID userId = user == null ? null : user.id().value();
        String email = user == null ? attemptedEmail : user.email().value();
        audit.recordAs(userId, tenantId.value(), AuditAction.LOGIN_FAILED,
                userId == null ? null : AuditTarget.of(AuditTarget.USER, userId),
                Map.of("email", email, "reason", reason));
    }

    private IssuedTokens issue(UUID userId, UUID tenantId, Instant now) {
        String rawRefreshToken = SecureTokens.random(REFRESH_TOKEN_BYTES);
        RefreshToken refreshToken = refreshTokens.save(new RefreshToken(userId, tenantId,
                SecureTokens.sha256(rawRefreshToken), now, now.plus(jwtProperties.refreshTokenTtl()), ClientIp.current()));
        String accessToken = accessTokens.forUser(userId, tenantId, accessService.roleNamesOf(tenantId, userId));
        TokenResponse response = new TokenResponse(accessToken, "Bearer", accessTokens.ttlSeconds(), rawRefreshToken,
                jwtProperties.refreshTokenTtl().toSeconds());
        return new IssuedTokens(refreshToken.getId(), response);
    }

    private record IssuedTokens(UUID refreshTokenId, TokenResponse response) {}
}
