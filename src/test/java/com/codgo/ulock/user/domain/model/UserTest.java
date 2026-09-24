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
import com.codgo.ulock.user.domain.event.UserRenamed;
import com.codgo.ulock.user.domain.exception.InvalidUserException;
import com.codgo.ulock.user.domain.exception.InvalidUserStateException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UserTest {

    private static final Instant T0 = Instant.parse("2026-09-23T10:00:00Z");
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
    void restoringKeepsEveryFieldAndRecordsNoEvents() {
        Instant locked = T0.plusSeconds(60);
        User restored = User.restore(user.id(), TENANT, user.email(), "hash", "Jane", UserStatus.INACTIVE, T0, 3,
                T0, locked, T0, T0.plusSeconds(1));

        assertThat(restored.status()).isEqualTo(UserStatus.INACTIVE);
        assertThat(restored.failedLoginCount()).isEqualTo(3);
        assertThat(restored.lockedUntil()).isEqualTo(locked);
        assertThat(restored.pullDomainEvents()).isEmpty();
    }

    @Nested
    class StatusTransitions {

        @ParameterizedTest(name = "{0} -> {1}: {2}")
        @CsvSource({"ACTIVE, INACTIVE, true", "INACTIVE, ACTIVE, true", "ACTIVE, ACTIVE, false", "INACTIVE, INACTIVE, false"})
        void allowedTransitionsAreExplicit(UserStatus from, UserStatus to, boolean allowed) {
            assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
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
    }

    @Nested
    class Administration {

        @Test
        void renameRecordsUserRenamedOnlyWhenTheNameChanges() {
            user.pullDomainEvents();

            assertThat(user.rename("Jane Doe", T0)).isFalse();
            assertThat(user.rename("Jane Smith", T0.plusSeconds(5))).isTrue();

            assertThat(user.updatedAt()).isEqualTo(T0.plusSeconds(5));
            assertThat(user.pullDomainEvents())
                    .containsExactly(new UserRenamed(user.id(), TENANT, "Jane Smith", T0.plusSeconds(5)));
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
            assertThat(fail(T0.plus(User.FAILED_LOGIN_WINDOW)).lockedNow()).isFalse();
            assertThat(user.failedLoginCount()).isEqualTo(1);
        }

        @Test
        void aLockedAccountRejectsEvenTheRightPasswordUntilTheLockExpires() {
            lockOut();

            assertThat(user.canAttemptLogin(T0)).isFalse();
            assertThat(user.recordLoginAttempt(true, T0).result()).isEqualTo(LoginAttempt.Result.LOCKED);
            assertThat(user.recordLoginAttempt(true, T0.plus(User.LOCK_DURATION)).succeeded()).isTrue();
        }

        @Test
        void inactiveUsersCannotLogIn() {
            user.deactivate(null, T0);

            assertThat(user.canAttemptLogin(T0)).isFalse();
            assertThat(user.recordLoginAttempt(true, T0).result()).isEqualTo(LoginAttempt.Result.INACTIVE);
        }

        @Test
        void aSuccessfulLoginRecordsTheTimeAndClearsFailures() {
            fail(T0);
            fail(T0);

            assertThat(user.recordLoginAttempt(true, T0.plusSeconds(1)).succeeded()).isTrue();
            assertThat(user.failedLoginCount()).isZero();
            assertThat(user.lastLoginAt()).isEqualTo(T0.plusSeconds(1));
        }
    }

    private LoginAttempt fail(Instant at) {
        return user.recordLoginAttempt(false, at);
    }

    private void lockOut() {
        for (int i = 0; i < User.MAX_FAILED_LOGINS; i++) {
            fail(T0);
        }
    }
}
