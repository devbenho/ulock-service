package com.codgo.ulock.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codgo.ulock.sharedkernel.event.DomainEvent;
import com.codgo.ulock.sharedkernel.event.DomainEventPublisherPort;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.user.api.CreateUserCommand;
import com.codgo.ulock.user.api.UserView;
import com.codgo.ulock.user.domain.EmailAlreadyInUseException;
import com.codgo.ulock.user.domain.InvalidPasswordException;
import com.codgo.ulock.user.domain.User;
import com.codgo.ulock.user.domain.UserCreated;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateUserHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final String PASSWORD = "correct-horse-battery";

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);
    private final List<DomainEvent> published = new ArrayList<>();
    private final DomainEventPublisherPort events = published::add;

    private CreateUserHandler handler;

    @BeforeEach
    void setUp() {
        when(hasher.hash(any())).thenAnswer(invocation -> "hashed:" + invocation.getArgument(0));
        handler = new CreateUserHandler(users, hasher, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void hashesThePasswordSavesAndPublishesUserCreated() {
        UserView view = handler.handle(new CreateUserCommand(TENANT, "Jane@Example.TEST", "Jane", PASSWORD));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().passwordHash()).isEqualTo("hashed:" + PASSWORD);
        assertThat(view.email()).isEqualTo(Email.of("jane@example.test"));
        assertThat(view.status()).isEqualTo("ACTIVE");
        assertThat(view.createdAt()).isEqualTo(NOW);
        assertThat(published).containsExactly(new UserCreated(view.id(), TENANT, view.email(), NOW));
    }

    @Test
    void rejectsAnEmailAlreadyUsedInTheTenant() {
        when(users.existsByEmail(TENANT, Email.of("taken@example.test"))).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(new CreateUserCommand(TENANT, "taken@example.test", "T", PASSWORD)))
                .isInstanceOf(EmailAlreadyInUseException.class);
        verify(users, never()).save(any());
        assertThat(published).isEmpty();
    }

    @Test
    void enforcesThePasswordPolicyBeforeHashing() {
        assertThatThrownBy(() -> handler.handle(new CreateUserCommand(TENANT, "a@b.test", "A", "short")))
                .isInstanceOf(InvalidPasswordException.class);
        verify(hasher, never()).hash(any());
    }
}
