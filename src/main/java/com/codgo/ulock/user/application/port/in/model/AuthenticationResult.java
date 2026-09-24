package com.codgo.ulock.user.application.port.in.model;

import java.time.Instant;
import java.util.Optional;

/**
 * @param user        the matched user; null for {@link Outcome#UNKNOWN_USER}
 * @param lockedUntil set only when this very attempt locked the account
 */
public record AuthenticationResult(Outcome outcome, UserView user, Instant lockedUntil) {

    public enum Outcome {
        AUTHENTICATED,
        UNKNOWN_USER,
        BAD_PASSWORD,
        LOCKED,
        INACTIVE
    }

    public boolean authenticated() {
        return outcome == Outcome.AUTHENTICATED;
    }

    public Optional<UserView> matchedUser() {
        return Optional.ofNullable(user);
    }

    public boolean lockedNow() {
        return lockedUntil != null;
    }
}
