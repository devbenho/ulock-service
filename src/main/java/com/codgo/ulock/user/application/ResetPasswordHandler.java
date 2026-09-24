package com.codgo.ulock.user.application;

import com.codgo.ulock.sharedkernel.event.DomainEventPublisherPort;
import com.codgo.ulock.user.api.UserCredentialsRevokedEvent;
import com.codgo.ulock.user.domain.PasswordPolicy;
import com.codgo.ulock.user.domain.User;
import com.codgo.ulock.user.domain.UserNotFoundException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** An administrator sets a new password; the user's sessions end and any lock is lifted. */
@Service
public class ResetPasswordHandler {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final AdministrationPolicy administrationPolicy;
    private final DomainEventPublisherPort events;
    private final Clock clock;

    ResetPasswordHandler(UserRepository users, PasswordHasher passwordHasher,
                         AdministrationPolicy administrationPolicy, DomainEventPublisherPort events, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.administrationPolicy = administrationPolicy;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void handle(ResetPasswordCommand command) {
        PasswordPolicy.validate(command.newPassword());
        User user = users.findById(command.tenantId(), command.userId())
                .orElseThrow(() -> new UserNotFoundException(command.userId()));
        administrationPolicy.requireCanAdminister(command.tenantId(), command.userId());
        Instant now = clock.instant();
        user.resetPassword(passwordHasher.hash(command.newPassword()), now);
        users.save(user);
        events.publishAll(user.pullDomainEvents());
        events.publish(new UserCredentialsRevokedEvent(command.userId(), now));
    }
}
