package com.codgo.ulock.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codgo.ulock.sharedkernel.event.DomainEvent;
import com.codgo.ulock.sharedkernel.event.DomainEventPublisherPort;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.api.UserCredentialsRevokedEvent;
import com.codgo.ulock.user.domain.User;
import com.codgo.ulock.user.domain.UserNotFoundException;
import com.codgo.ulock.user.domain.UserPasswordReset;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResetPasswordHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);
    private final AdministrationPolicy administrationPolicy = mock(AdministrationPolicy.class);
    private final List<DomainEvent> published = new ArrayList<>();
    private final DomainEventPublisherPort events = published::add;

    private ResetPasswordHandler handler;

    @BeforeEach
    void setUp() {
        when(hasher.hash(any())).thenAnswer(invocation -> "hashed:" + invocation.getArgument(0));
        handler = new ResetPasswordHandler(users, hasher, administrationPolicy, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void usersOutsideTheTenantAreNotFound() {
        UserId id = UserId.newId();
        when(users.findById(TENANT, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ResetPasswordCommand(TENANT, id, "a-new-password")))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void revokesSessions() {
        User user = existingUser();

        handler.handle(new ResetPasswordCommand(TENANT, user.id(), "a-new-password"));

        verify(administrationPolicy).requireCanAdminister(TENANT, user.id());
        assertThat(user.passwordHash()).isEqualTo("hashed:a-new-password");
        assertThat(published).containsExactly(new UserPasswordReset(user.id(), TENANT, NOW),
                new UserCredentialsRevokedEvent(user.id(), NOW));
    }

    private User existingUser() {
        User user = User.register(TENANT, Email.of("existing@example.test"), "hash", "Existing", NOW.minusSeconds(60));
        user.pullDomainEvents();
        when(users.findById(TENANT, user.id())).thenReturn(Optional.of(user));
        return user;
    }
}
