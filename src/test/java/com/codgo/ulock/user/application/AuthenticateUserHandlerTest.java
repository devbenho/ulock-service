package com.codgo.ulock.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.user.api.AuthenticationResult.Outcome;
import com.codgo.ulock.user.api.AuthenticationResult;
import com.codgo.ulock.user.api.AuthenticateUserCommand;
import com.codgo.ulock.user.application.PasswordHasher;
import com.codgo.ulock.user.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthenticateUserHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final Email EMAIL = Email.of("u@example.test");

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);
    private final AuthenticateUserHandler handler = new AuthenticateUserHandler(users, hasher,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private User user;

    @BeforeEach
    void setUp() {
        user = User.register(TENANT, EMAIL, "stored-hash", "U", NOW.minusSeconds(60));
        when(users.findByEmailForUpdate(TENANT, EMAIL)).thenReturn(Optional.of(user));
        when(hasher.matches("right", "stored-hash")).thenReturn(true);
    }

    @Test
    void authenticatesWithTheRightPasswordLookingUpTheNormalisedEmail() {
        AuthenticationResult result = authenticate("U@Example.TEST", "right");

        assertThat(result.authenticated()).isTrue();
        assertThat(result.user().id()).isEqualTo(user.id());
        verify(users).save(user);
    }

    @Test
    void unknownAndMalformedEmailsSpendADummyComparison() {
        assertThat(authenticate("ghost@example.test", "pw").outcome()).isEqualTo(Outcome.UNKNOWN_USER);
        assertThat(authenticate("not an email", "pw").outcome()).isEqualTo(Outcome.UNKNOWN_USER);

        verify(hasher, org.mockito.Mockito.times(2)).simulateMatch("pw");
        verify(users, never()).save(any());
    }

    @Test
    void reportsTheAttemptThatLocksTheAccount() {
        failUntilOneAttemptBeforeLock();
        AuthenticationResult locking = authenticate(EMAIL.value(), "wrong");

        assertThat(locking.outcome()).isEqualTo(Outcome.BAD_PASSWORD);
        assertThat(locking.lockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void lockedAccountsDoNotCheckTheRealPassword() {
        failUntilOneAttemptBeforeLock();
        authenticate(EMAIL.value(), "wrong");

        AuthenticationResult result = authenticate(EMAIL.value(), "right");

        assertThat(result.outcome()).isEqualTo(Outcome.LOCKED);
        assertThat(result.lockedNow()).isFalse();
        verify(hasher, never()).matches("right", "stored-hash");
        verify(hasher).simulateMatch("right");
    }

    private void failUntilOneAttemptBeforeLock() {
        for (int i = 1; i < User.MAX_FAILED_LOGINS; i++) {
            assertThat(authenticate(EMAIL.value(), "wrong").lockedNow()).isFalse();
        }
    }

    @Test
    void inactiveUsersAreRejected() {
        user.deactivate(null, NOW);

        assertThat(authenticate(EMAIL.value(), "right").outcome()).isEqualTo(Outcome.INACTIVE);
    }
    private AuthenticationResult authenticate(String email, String password) {
        return handler.handle(new AuthenticateUserCommand(TENANT, email, password));
    }
}
