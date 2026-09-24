package com.codgo.ulock.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codgo.ulock.sharedkernel.event.DomainEvent;
import com.codgo.ulock.sharedkernel.event.DomainEventPublisherPort;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.api.CreateUserCommand;
import com.codgo.ulock.user.api.UserCredentialsRevokedEvent;
import com.codgo.ulock.user.api.UserView;
import com.codgo.ulock.user.application.LoadUserPort;
import com.codgo.ulock.user.application.SaveUserPort;
import com.codgo.ulock.user.application.AdministrationPolicyPort;
import com.codgo.ulock.user.application.PasswordHasherPort;
import com.codgo.ulock.user.application.UpdateUserCommand;
import com.codgo.ulock.user.domain.UserCreated;
import com.codgo.ulock.user.domain.UserDeactivated;
import com.codgo.ulock.user.domain.UserPasswordReset;
import com.codgo.ulock.user.domain.UserRenamed;
import com.codgo.ulock.user.domain.EmailAlreadyInUseException;
import com.codgo.ulock.user.domain.InvalidPasswordException;
import com.codgo.ulock.user.domain.UserNotFoundException;
import com.codgo.ulock.user.domain.User;
import com.codgo.ulock.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final String PASSWORD = "correct-horse-battery";

    private final LoadUserPort loadUsers = mock(LoadUserPort.class);
    private final SaveUserPort saveUsers = mock(SaveUserPort.class);
    private final PasswordHasherPort hasher = mock(PasswordHasherPort.class);
    private final AdministrationPolicyPort administrationPolicy = mock(AdministrationPolicyPort.class);
    private final List<DomainEvent> published = new ArrayList<>();
    private final DomainEventPublisherPort events = published::add;

    private UserService service;

    @BeforeEach
    void setUp() {
        when(hasher.hash(any())).thenAnswer(invocation -> "hashed:" + invocation.getArgument(0));
        service = new UserService(loadUsers, saveUsers, hasher, administrationPolicy, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createHashesThePasswordSavesAndPublishesUserCreated() {
        UserView view = service.createUser(new CreateUserCommand(TENANT, "Jane@Example.TEST", "Jane", PASSWORD));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(saveUsers).save(saved.capture());
        assertThat(saved.getValue().passwordHash()).isEqualTo("hashed:" + PASSWORD);
        assertThat(view.email()).isEqualTo(Email.of("jane@example.test"));
        assertThat(view.status()).isEqualTo("ACTIVE");
        assertThat(view.createdAt()).isEqualTo(NOW);
        assertThat(published).containsExactly(new UserCreated(view.id(), TENANT, view.email(), NOW));
    }

    @Test
    void createRejectsAnEmailAlreadyUsedInTheTenant() {
        when(loadUsers.existsByEmail(TENANT, Email.of("taken@example.test"))).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(new CreateUserCommand(TENANT, "taken@example.test", "T", PASSWORD)))
                .isInstanceOf(EmailAlreadyInUseException.class);
        verify(saveUsers, never()).save(any());
        assertThat(published).isEmpty();
    }

    @Test
    void createEnforcesThePasswordPolicyBeforeHashing() {
        assertThatThrownBy(() -> service.createUser(new CreateUserCommand(TENANT, "a@b.test", "A", "short")))
                .isInstanceOf(InvalidPasswordException.class);
        verify(hasher, never()).hash(any());
    }

    @Test
    void commandsOnUsersOutsideTheTenantAreNotFound() {
        UserId id = UserId.newId();
        when(loadUsers.findById(TENANT, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(new UpdateUserCommand(TENANT, id, "X", null, null)))
                .isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> service.resetPassword(TENANT, id, "a-new-password"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void deactivationChecksThePolicyAndRevokesSessions() {
        User user = existingUser();
        UserId admin = UserId.newId();

        UserView updated = service.update(new UpdateUserCommand(TENANT, user.id(), null, UserStatus.INACTIVE, admin));

        assertThat(updated.status()).isEqualTo("INACTIVE");
        verify(administrationPolicy).requireCanAdminister(TENANT, user.id());
        verify(saveUsers).save(user);
        assertThat(published).containsExactly(new UserDeactivated(user.id(), TENANT, NOW),
                new UserCredentialsRevokedEvent(user.id(), NOW));
    }

    @Test
    void aRejectedPolicyCheckChangesNothing() {
        User user = existingUser();
        doThrow(new IllegalStateException("forbidden")).when(administrationPolicy).requireCanAdminister(TENANT, user.id());

        assertThatThrownBy(() -> service.update(new UpdateUserCommand(TENANT, user.id(), null, UserStatus.INACTIVE, null)))
                .hasMessage("forbidden");
        verify(saveUsers, never()).save(any());
        assertThat(published).isEmpty();
    }

    @Test
    void renamingNeedsNoPolicyCheckAndDoesNotRevokeSessions() {
        User user = existingUser();

        service.update(new UpdateUserCommand(TENANT, user.id(), "New Name", null, null));

        verify(administrationPolicy, never()).requireCanAdminister(any(), any());
        assertThat(published).containsExactly(new UserRenamed(user.id(), TENANT, "New Name", NOW));
    }

    @Test
    void settingTheCurrentStatusAgainIsANoOp() {
        User user = existingUser();

        service.update(new UpdateUserCommand(TENANT, user.id(), null, UserStatus.ACTIVE, null));

        verify(administrationPolicy, never()).requireCanAdminister(any(), any());
        assertThat(published).isEmpty();
    }

    @Test
    void passwordResetRevokesSessions() {
        User user = existingUser();

        service.resetPassword(TENANT, user.id(), "a-new-password");

        verify(administrationPolicy).requireCanAdminister(TENANT, user.id());
        assertThat(user.passwordHash()).isEqualTo("hashed:a-new-password");
        assertThat(published).containsExactly(new UserPasswordReset(user.id(), TENANT, NOW),
                new UserCredentialsRevokedEvent(user.id(), NOW));
    }

    private User existingUser() {
        User user = User.register(TENANT, Email.of("existing@example.test"), "hash", "Existing", NOW.minusSeconds(60));
        user.pullDomainEvents();
        when(loadUsers.findById(TENANT, user.id())).thenReturn(Optional.of(user));
        return user;
    }
}
