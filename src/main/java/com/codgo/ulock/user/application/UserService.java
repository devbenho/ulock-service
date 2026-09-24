package com.codgo.ulock.user.application;

import com.codgo.ulock.sharedkernel.event.DomainEventPublisherPort;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.api.CreateUserUseCase;
import com.codgo.ulock.user.api.CreateUserCommand;
import com.codgo.ulock.user.api.UserCredentialsRevokedEvent;
import com.codgo.ulock.user.api.UserView;
import com.codgo.ulock.user.application.LoadUserPort;
import com.codgo.ulock.user.application.SaveUserPort;
import com.codgo.ulock.user.application.AdministrationPolicyPort;
import com.codgo.ulock.user.application.PasswordHasherPort;
import com.codgo.ulock.user.application.UpdateUserCommand;
import com.codgo.ulock.user.application.UserViews;
import com.codgo.ulock.user.domain.EmailAlreadyInUseException;
import com.codgo.ulock.user.domain.UserNotFoundException;
import com.codgo.ulock.user.domain.User;
import com.codgo.ulock.user.domain.UserStatus;
import com.codgo.ulock.user.domain.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User administration commands within a tenant: create, rename, activate/deactivate, reset password. */
@Service
public class UserService implements CreateUserUseCase {

    private final LoadUserPort loadUsers;
    private final SaveUserPort saveUsers;
    private final PasswordHasherPort passwordHasher;
    private final AdministrationPolicyPort administrationPolicy;
    private final DomainEventPublisherPort events;
    private final Clock clock;

    public UserService(LoadUserPort loadUsers, SaveUserPort saveUsers, PasswordHasherPort passwordHasher,
                       AdministrationPolicyPort administrationPolicy, DomainEventPublisherPort events, Clock clock) {
        this.loadUsers = loadUsers;
        this.saveUsers = saveUsers;
        this.passwordHasher = passwordHasher;
        this.administrationPolicy = administrationPolicy;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UserView createUser(CreateUserCommand command) {
        PasswordPolicy.validate(command.password());
        Email email = Email.of(command.email());
        if (loadUsers.existsByEmail(command.tenantId(), email)) {
            throw new EmailAlreadyInUseException();
        }
        User user = User.register(command.tenantId(), email, passwordHasher.hash(command.password()),
                command.fullName(), clock.instant());
        saveAndPublish(user);
        return UserViews.from(user);
    }

    @Transactional
    public UserView update(UpdateUserCommand command) {
        User user = load(command.tenantId(), command.userId());
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
        saveAndPublish(user);
        if (deactivated) {
            events.publish(new UserCredentialsRevokedEvent(user.id(), now));
        }
        return UserViews.from(user);
    }

    /** An administrator sets a new password; the user's sessions end and any lock is lifted. */
    @Transactional
    public void resetPassword(TenantId tenantId, UserId userId, String newPassword) {
        PasswordPolicy.validate(newPassword);
        User user = load(tenantId, userId);
        administrationPolicy.requireCanAdminister(tenantId, userId);
        Instant now = clock.instant();
        user.resetPassword(passwordHasher.hash(newPassword), now);
        saveAndPublish(user);
        events.publish(new UserCredentialsRevokedEvent(userId, now));
    }

    private User load(TenantId tenantId, UserId userId) {
        return loadUsers.findById(tenantId, userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    private void saveAndPublish(User user) {
        saveUsers.save(user);
        events.publishAll(user.pullDomainEvents());
    }
}
