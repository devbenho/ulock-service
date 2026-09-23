package com.codgo.ulock.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final int MAX = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCK = Duration.ofMinutes(15);
    private static final Instant T0 = Instant.parse("2026-09-23T10:00:00Z");

    private final User user = new User(UUID.randomUUID(), "u@example.test", "hash", "U");

    @Test
    void locksOnTheFifthFailureInsideTheWindow() {
        for (int i = 0; i < 4; i++) {
            assertThat(fail(T0.plusSeconds(i * 60L))).isFalse();
        }
        assertThat(fail(T0.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(user.isLocked(T0.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(user.getLockedUntil()).isEqualTo(T0.plus(Duration.ofMinutes(29)));
    }

    @Test
    void failuresOutsideTheWindowStartANewCount() {
        for (int i = 0; i < 4; i++) {
            fail(T0);
        }
        assertThat(fail(T0.plus(WINDOW))).isFalse();
        assertThat(user.getFailedLoginCount()).isEqualTo(1);
    }

    @Test
    void theLockExpires() {
        for (int i = 0; i < MAX; i++) {
            fail(T0);
        }
        assertThat(user.isLocked(T0.plus(LOCK).minusSeconds(1))).isTrue();
        assertThat(user.isLocked(T0.plus(LOCK))).isFalse();
    }

    @Test
    void aSuccessfulLoginClearsTheFailureCount() {
        fail(T0);
        fail(T0);
        user.registerSuccessfulLogin(T0.plusSeconds(1));

        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLastLoginAt()).isEqualTo(T0.plusSeconds(1));
    }

    @Test
    void changingThePasswordLiftsTheLock() {
        for (int i = 0; i < MAX; i++) {
            fail(T0);
        }
        user.changePassword("new-hash");

        assertThat(user.isLocked(T0)).isFalse();
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
    }

    @Test
    void emailsAreNormalised() {
        assertThat(new User(UUID.randomUUID(), " Mixed@Case.Test ", "h", "n").getEmail()).isEqualTo("mixed@case.test");
    }

    private boolean fail(Instant at) {
        return user.registerFailedLogin(at, MAX, WINDOW, LOCK);
    }
}
