package com.codgo.ulock.user.application;

import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.user.api.AuthenticateUserCommand;
import com.codgo.ulock.user.api.AuthenticationResult;
import com.codgo.ulock.user.api.AuthenticationResult.Outcome;
import com.codgo.ulock.user.domain.LoginAttempt;
import com.codgo.ulock.user.domain.User;
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
public class AuthenticateUserHandler {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    AuthenticateUserHandler(UserRepository users, PasswordHasher passwordHasher, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    @Transactional
    public AuthenticationResult handle(AuthenticateUserCommand command) {
        Optional<User> candidate = Email.tryParse(command.email())
                .flatMap(address -> users.findByEmailForUpdate(command.tenantId(), address));
        if (candidate.isEmpty()) {
            passwordHasher.simulateMatch(command.password());
            return new AuthenticationResult(Outcome.UNKNOWN_USER, null, null);
        }
        User user = candidate.get();
        Instant now = clock.instant();
        boolean matched;
        if (user.canAttemptLogin(now)) {
            matched = passwordHasher.matches(command.password(), user.passwordHash());
        } else {
            passwordHasher.simulateMatch(command.password());
            matched = false;
        }
        LoginAttempt attempt = user.recordLoginAttempt(matched, now);
        users.save(user);
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
