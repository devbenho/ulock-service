package com.codgo.ulock.user.application.service;

import com.codgo.ulock.sharedkernel.event.DomainEventPublisherPort;
import com.codgo.ulock.sharedkernel.paging.PageQuery;
import com.codgo.ulock.sharedkernel.paging.PageResult;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.application.port.in.CreateUserUseCase;
import com.codgo.ulock.user.application.port.in.command.CreateUserCommand;
import com.codgo.ulock.user.application.port.in.event.UserCredentialsRevokedEvent;
import com.codgo.ulock.user.application.port.in.model.UserView;
import com.codgo.ulock.user.application.port.out.persistence.LoadUserPort;
import com.codgo.ulock.user.application.port.out.persistence.SaveUserPort;
import com.codgo.ulock.user.application.port.out.persistence.UserFilter;
import com.codgo.ulock.user.application.port.out.security.AdministrationPolicyPort;
import com.codgo.ulock.user.application.port.out.security.PasswordHasherPort;
import com.codgo.ulock.user.application.service.command.UpdateUserCommand;
import com.codgo.ulock.user.application.service.mapper.UserViews;
import com.codgo.ulock.user.domain.exception.EmailAlreadyInUseException;
import com.codgo.ulock.user.domain.exception.UserNotFoundException;
import com.codgo.ulock.user.domain.model.User;
import com.codgo.ulock.user.domain.model.UserStatus;
import com.codgo.ulock.user.domain.policy.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administration of users within a tenant: create, list, read, update, deactivate, reset password. */
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

    @Transactional
    public User create(CreateUserCommand command) {
        PasswordPolicy.validate(command.password());
        Email email = Email.of(command.email());
        if (loadUsers.existsByEmail(command.tenantId(), email)) {
            throw new EmailAlreadyInUseException();
        }
        User user = User.register(command.tenantId(), email, passwordHasher.hash(command.password()),
                command.fullName(), clock.instant());
        saveAndPublish(user);
        return user;
    }

    @Override
    @Transactional
    public UserView createUser(CreateUserCommand command) {
        return UserViews.from(create(command));
    }

    @Transactional(readOnly = true)
    public PageResult<User> list(TenantId tenantId, UserStatus status, PageQuery page) {
        return loadUsers.loadUsers(tenantId, status == null ? UserFilter.none() : UserFilter.byStatus(status), page);
    }

    @Transactional(readOnly = true)
    public User get(TenantId tenantId, UserId userId) {
        return loadUsers.loadUser(tenantId, userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Transactional
    public User update(UpdateUserCommand command) {
        User user = get(command.tenantId(), command.userId());
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
        return user;
    }

    /** An administrator sets a new password; the user's sessions end and any lock is lifted. */
    @Transactional
    public void resetPassword(TenantId tenantId, UserId userId, String newPassword) {
        PasswordPolicy.validate(newPassword);
        User user = get(tenantId, userId);
        administrationPolicy.requireCanAdminister(tenantId, userId);
        Instant now = clock.instant();
        user.resetPassword(passwordHasher.hash(newPassword), now);
        saveAndPublish(user);
        events.publish(new UserCredentialsRevokedEvent(userId, now));
    }

    private void saveAndPublish(User user) {
        saveUsers.save(user);
        events.publishAll(user.pullDomainEvents());
    }
}
