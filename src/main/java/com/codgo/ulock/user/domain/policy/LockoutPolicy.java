package com.codgo.ulock.user.domain.policy;

import java.time.Duration;
import java.util.Objects;

/** Locks an account after {@code maxFailures} failed logins within {@code window}, for {@code lockDuration}. */
public record LockoutPolicy(int maxFailures, Duration window, Duration lockDuration) {

    public LockoutPolicy {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(lockDuration, "lockDuration");
        if (maxFailures < 1 || window.isNegative() || window.isZero() || lockDuration.isNegative()) {
            throw new IllegalArgumentException("Invalid lockout policy");
        }
    }
}
