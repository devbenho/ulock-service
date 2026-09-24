package com.codgo.ulock.user.application;

import com.codgo.ulock.sharedkernel.event.DomainEventPublisherPort;
import com.codgo.ulock.user.api.UserCredentialsRevokedEvent;
import com.codgo.ulock.user.api.UserView;
import com.codgo.ulock.user.domain.User;
import com.codgo.ulock.user.domain.UserNotFoundException;
import com.codgo.ulock.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Renames a user and moves them between ACTIVE and INACTIVE. Null fields are left unchanged. */
@Service
public class UpdateUserHandler {

    private final UserRepository users;
    private final AdministrationPolicy administrationPolicy;
    private final DomainEventPublisherPort events;
    private final Clock clock;

    UpdateUserHandler(UserRepository users, AdministrationPolicy administrationPolicy,
                      DomainEventPublisherPort events, Clock clock) {
        this.users = users;
        this.administrationPolicy = administrationPolicy;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public UserView handle(UpdateUserCommand command) {
        User user = users.findById(command.tenantId(), command.userId())
                .orElseThrow(() -> new UserNotFoundException(command.userId()));
        Instant now = clock.instant();
        if (command.fullName() != null) {
            user.rename(command.fullName(), now);
        }
        boolean deactivated = false;
        if (command.status() != null && command.status() != user.status()) {
            administrationPolicy.requireCanAdminister(command.tenantId(), command.userId());
            if (command.status() == UserStatus.INACTIVE) {
                deactivated = user.deactivate(command.actor(), now);
            } else {
                user.activate(now);
            }
        }
        users.save(user);
        events.publishAll(user.pullDomainEvents());
        if (deactivated) {
            events.publish(new UserCredentialsRevokedEvent(user.id(), now));
        }
        return UserViews.from(user);
    }
}
