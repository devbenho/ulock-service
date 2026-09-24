package com.codgo.ulock.user.application;

import com.codgo.ulock.sharedkernel.event.DomainEventPublisherPort;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.user.api.CreateUserCommand;
import com.codgo.ulock.user.api.UserView;
import com.codgo.ulock.user.domain.EmailAlreadyInUseException;
import com.codgo.ulock.user.domain.PasswordPolicy;
import com.codgo.ulock.user.domain.User;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registers a user in a tenant. Email uniqueness is a cross-aggregate check, so it lives here. */
@Service
public class CreateUserHandler {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final DomainEventPublisherPort events;
    private final Clock clock;

    CreateUserHandler(UserRepository users, PasswordHasher passwordHasher, DomainEventPublisherPort events,
                      Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public UserView handle(CreateUserCommand command) {
        PasswordPolicy.validate(command.password());
        Email email = Email.of(command.email());
        if (users.existsByEmail(command.tenantId(), email)) {
            throw new EmailAlreadyInUseException();
        }
        User user = User.register(command.tenantId(), email, passwordHasher.hash(command.password()),
                command.fullName(), clock.instant());
        users.save(user);
        events.publishAll(user.pullDomainEvents());
        return UserViews.from(user);
    }
}
