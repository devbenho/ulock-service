package com.codgo.ulock.user.application.service;

import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.user.application.port.in.AuthenticateUserUseCase;
import com.codgo.ulock.user.application.port.in.model.AuthenticationResult.Outcome;
import com.codgo.ulock.user.application.port.in.model.AuthenticationResult;
import com.codgo.ulock.user.application.port.out.persistence.LoadUserPort;
import com.codgo.ulock.user.application.port.out.persistence.SaveUserPort;
import com.codgo.ulock.user.application.port.out.security.PasswordHasherPort;
import com.codgo.ulock.user.application.service.mapper.UserViews;
import com.codgo.ulock.user.domain.model.LoginAttempt;
import com.codgo.ulock.user.domain.model.User;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Password verification with lockout. Every path spends one hash comparison, so response timing does
 * not reveal whether the account exists or why the attempt failed.
 */
@Service
public class AuthenticateUserService implements AuthenticateUserUseCase {

    private final LoadUserPort loadUsers;
    private final SaveUserPort saveUsers;
    private final PasswordHasherPort passwordHasher;
    private final Clock clock;

    public AuthenticateUserService(LoadUserPort loadUsers, SaveUserPort saveUsers, PasswordHasherPort passwordHasher,
                                   Clock clock) {
        this.loadUsers = loadUsers;
        this.saveUsers = saveUsers;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AuthenticationResult authenticate(TenantId tenantId, String email, String password) {
        Optional<User> candidate = Email.tryParse(email).flatMap(address -> loadUsers.findByEmailForUpdate(tenantId, address));
        if (candidate.isEmpty()) {
            passwordHasher.simulateMatch(password);
            return new AuthenticationResult(Outcome.UNKNOWN_USER, null, null);
        }
        User user = candidate.get();
        Instant now = clock.instant();
        boolean matched;
        if (user.canAttemptLogin(now)) {
            matched = passwordHasher.matches(password, user.passwordHash());
        } else {
            passwordHasher.simulateMatch(password);
            matched = false;
        }
        LoginAttempt attempt = user.recordLoginAttempt(matched, now);
        saveUsers.save(user);
        return new AuthenticationResult(outcomeOf(attempt), UserViews.from(user),
                attempt.lockedNow() ? user.lockedUntil() : null);
    }

    private static Outcome outcomeOf(LoginAttempt attempt) {
        return switch (attempt.result()) {
            case SUCCEEDED -> Outcome.AUTHENTICATED;
            case BAD_PASSWORD -> Outcome.BAD_PASSWORD;
            case LOCKED -> Outcome.LOCKED;
            case INACTIVE -> Outcome.INACTIVE;
        };
    }
}
