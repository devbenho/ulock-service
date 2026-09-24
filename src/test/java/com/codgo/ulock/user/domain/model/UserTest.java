package com.codgo.ulock.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.domain.event.UserActivated;
import com.codgo.ulock.user.domain.event.UserCreated;
import com.codgo.ulock.user.domain.event.UserDeactivated;
import com.codgo.ulock.user.domain.event.UserPasswordReset;
import com.codgo.ulock.user.domain.event.UserUpdated;
import com.codgo.ulock.user.domain.exception.InvalidUserException;
import com.codgo.ulock.user.domain.exception.InvalidUserStateException;
import com.codgo.ulock.user.domain.policy.LockoutPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant T0 = Instant.parse("2026-09-23T10:00:00Z");
    private static final LockoutPolicy POLICY = new LockoutPolicy(5, Duration.ofMinutes(15), Duration.ofMinutes(15));
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());

    private final User user = User.register(TENANT, Email.of("u@example.test"), "hash", "  Jane Doe ", T0);

    @Test
    void registeringCreatesAnActiveUserAndRecordsUserCreated() {
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.fullName()).isEqualTo("Jane Doe");
        assertThat(user.createdAt()).isEqualTo(T0);
        assertThat(user.pullDomainEvents()).containsExactly(new UserCreated(user.id(), TENANT, user.email(), T0));
        assertThat(user.pullDomainEvents()).isEmpty();
    }

    @Test
    void namesMustNotBeBlankOrTooLong() {
        assertThatThrownBy(() -> User.register(TENANT, Email.of("a@b.test"), "h", "   ", T0))
                .isInstanceOf(InvalidUserException.class);
        assertThatThrownBy(() -> user.rename("x".repeat(User.MAX_NAME_LENGTH + 1), T0))
                .isInstanceOf(InvalidUserException.class);
    }

    @Test
    void snapshotsRoundTrip() {
        User copy = User.fromSnapshot(user.toSnapshot());

        assertThat(copy.toSnapshot()).isEqualTo(user.toSnapshot());
        assertThat(copy.pullDomainEvents()).isEmpty();
    }

    @Nested
    class Administration {

        @Test
        void renameRecordsAnEventOnlyWhenTheNameChanges() {
            user.pullDomainEvents();

            assertThat(user.rename("Jane Doe", T0)).isFalse();
            assertThat(user.rename("Jane Smith", T0.plusSeconds(5))).isTrue();

            assertThat(user.updatedAt()).isEqualTo(T0.plusSeconds(5));
            assertThat(user.pullDomainEvents()).containsExactly(new UserUpdated(user.id(), TENANT, "Jane Smith", T0.plusSeconds(5)));
        }

        @Test
        void aDeactivatedUserStaysInactiveUntilExplicitlyActivated() {
            user.pullDomainEvents();

            assertThat(user.deactivate(UserId.newId(), T0)).isTrue();
            assertThat(user.deactivate(UserId.newId(), T0)).isFalse();
            assertThat(user.isActive()).isFalse();

            assertThat(user.activate(T0)).isTrue();
            assertThat(user.activate(T0)).isFalse();
            assertThat(user.pullDomainEvents())
                    .containsExactly(new UserDeactivated(user.id(), TENANT, T0), new UserActivated(user.id(), TENANT, T0));
        }

        @Test
        void nobodyCanDeactivateThemselves() {
            assertThatThrownBy(() -> user.deactivate(user.id(), T0))
                    .isInstanceOf(InvalidUserStateException.class)
                    .hasMessage("You cannot deactivate your own account");
            assertThat(user.isActive()).isTrue();
        }

        @Test
        void systemCallersWithoutAnActorMayDeactivate() {
            assertThat(user.deactivate(null, T0)).isTrue();
        }

        @Test
        void passwordResetReplacesTheHashLiftsTheLockAndRecordsAnEvent() {
            lockOut();
            user.pullDomainEvents();

            user.resetPassword("new-hash", T0);

            assertThat(user.passwordHash()).isEqualTo("new-hash");
            assertThat(user.isLocked(T0)).isFalse();
            assertThat(user.pullDomainEvents()).containsExactly(new UserPasswordReset(user.id(), TENANT, T0));
        }
    }

    @Nested
    class Lockout {

        @Test
        void locksOnTheFifthFailureInsideTheWindow() {
            for (int i = 0; i < 4; i++) {
                assertThat(fail(T0.plusSeconds(i * 60L)).lockedNow()).isFalse();
            }
            LoginAttempt fifth = fail(T0.plus(Duration.ofMinutes(14)));

            assertThat(fifth).isEqualTo(new LoginAttempt(LoginAttempt.Result.BAD_PASSWORD, true));
            assertThat(user.lockedUntil()).isEqualTo(T0.plus(Duration.ofMinutes(29)));
        }

        @Test
        void failuresOutsideTheWindowStartANewCount() {
            for (int i = 0; i < 4; i++) {
                fail(T0);
            }
            assertThat(fail(T0.plus(POLICY.window())).lockedNow()).isFalse();
            assertThat(user.failedLoginCount()).isEqualTo(1);
        }

        @Test
        void aLockedAccountRejectsEvenTheRightPasswordUntilTheLockExpires() {
            lockOut();

            assertThat(user.canAttemptLogin(T0)).isFalse();
            assertThat(user.recordLoginAttempt(true, POLICY, T0).result()).isEqualTo(LoginAttempt.Result.LOCKED);
            assertThat(user.recordLoginAttempt(true, POLICY, T0.plus(POLICY.lockDuration())).succeeded()).isTrue();
        }

        @Test
        void inactiveUsersCannotLogIn() {
            user.deactivate(null, T0);

            assertThat(user.canAttemptLogin(T0)).isFalse();
            assertThat(user.recordLoginAttempt(true, POLICY, T0).result()).isEqualTo(LoginAttempt.Result.INACTIVE);
        }

        @Test
        void aSuccessfulLoginRecordsTheTimeAndClearsFailures() {
            fail(T0);
            fail(T0);

            assertThat(user.recordLoginAttempt(true, POLICY, T0.plusSeconds(1)).succeeded()).isTrue();
            assertThat(user.failedLoginCount()).isZero();
            assertThat(user.lastLoginAt()).isEqualTo(T0.plusSeconds(1));
        }
    }

    private LoginAttempt fail(Instant at) {
        return user.recordLoginAttempt(false, POLICY, at);
    }

    private void lockOut() {
        for (int i = 0; i < POLICY.maxFailures(); i++) {
            fail(T0);
        }
    }
}
