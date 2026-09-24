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
import com.codgo.ulock.user.api.UserCredentialsRevokedEvent;
import com.codgo.ulock.user.api.UserView;
import com.codgo.ulock.user.domain.User;
import com.codgo.ulock.user.domain.UserDeactivated;
import com.codgo.ulock.user.domain.UserNotFoundException;
import com.codgo.ulock.user.domain.UserRenamed;
import com.codgo.ulock.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdateUserHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());

    private final UserRepository users = mock(UserRepository.class);
    private final AdministrationPolicy administrationPolicy = mock(AdministrationPolicy.class);
    private final List<DomainEvent> published = new ArrayList<>();
    private final DomainEventPublisherPort events = published::add;
    private final UpdateUserHandler handler =
            new UpdateUserHandler(users, administrationPolicy, events, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void usersOutsideTheTenantAreNotFound() {
        UserId id = UserId.newId();
        when(users.findById(TENANT, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new UpdateUserCommand(TENANT, id, "X", null, null)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void deactivationChecksThePolicyAndRevokesSessions() {
        User user = existingUser();
        UserId admin = UserId.newId();

        UserView updated = handler.handle(new UpdateUserCommand(TENANT, user.id(), null, UserStatus.INACTIVE, admin));

        assertThat(updated.status()).isEqualTo("INACTIVE");
        verify(administrationPolicy).requireCanAdminister(TENANT, user.id());
        verify(users).save(user);
        assertThat(published).containsExactly(new UserDeactivated(user.id(), TENANT, NOW),
                new UserCredentialsRevokedEvent(user.id(), NOW));
    }

    @Test
    void aRejectedPolicyCheckChangesNothing() {
        User user = existingUser();
        doThrow(new IllegalStateException("forbidden")).when(administrationPolicy).requireCanAdminister(TENANT, user.id());

        assertThatThrownBy(() -> handler.handle(new UpdateUserCommand(TENANT, user.id(), null, UserStatus.INACTIVE, null)))
                .hasMessage("forbidden");
        verify(users, never()).save(any());
        assertThat(published).isEmpty();
    }

    @Test
    void renamingNeedsNoPolicyCheckAndDoesNotRevokeSessions() {
        User user = existingUser();

        handler.handle(new UpdateUserCommand(TENANT, user.id(), "New Name", null, null));

        verify(administrationPolicy, never()).requireCanAdminister(any(), any());
        assertThat(published).containsExactly(new UserRenamed(user.id(), TENANT, "New Name", NOW));
    }

    @Test
    void settingTheCurrentStatusAgainIsANoOp() {
        User user = existingUser();

        handler.handle(new UpdateUserCommand(TENANT, user.id(), null, UserStatus.ACTIVE, null));

        verify(administrationPolicy, never()).requireCanAdminister(any(), any());
        assertThat(published).isEmpty();
    }

    private User existingUser() {
        User user = User.register(TENANT, Email.of("existing@example.test"), "hash", "Existing", NOW.minusSeconds(60));
        user.pullDomainEvents();
        when(users.findById(TENANT, user.id())).thenReturn(Optional.of(user));
        return user;
    }
}
