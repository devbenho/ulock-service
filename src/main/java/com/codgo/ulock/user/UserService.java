package com.codgo.ulock.user;

import com.codgo.ulock.audit.AuditAction;
import com.codgo.ulock.audit.AuditService;
import com.codgo.ulock.audit.AuditTarget;
import com.codgo.ulock.common.error.ConflictException;
import com.codgo.ulock.common.error.NotFoundException;
import com.codgo.ulock.common.security.CurrentActor;
import com.codgo.ulock.user.UserDtos.CreateUserRequest;
import com.codgo.ulock.user.UserDtos.UpdateUserRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final UserAdministrationPolicy administrationPolicy;

    UserService(UserRepository users, PasswordEncoder passwordEncoder, AuditService audit,
                ApplicationEventPublisher events, UserAdministrationPolicy administrationPolicy) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.events = events;
        this.administrationPolicy = administrationPolicy;
    }

    @Transactional
    public User create(UUID tenantId, CreateUserRequest request) {
        PasswordPolicy.validate(request.password());
        String email = User.normalizeEmail(request.email());
        if (users.existsByTenantIdAndEmail(tenantId, email)) {
            throw new ConflictException("A user with this email already exists in the tenant");
        }
        User user = users.save(new User(tenantId, email, passwordEncoder.encode(request.password()),
                request.fullName().trim()));
        audit.record(tenantId, AuditAction.USER_CREATED, AuditTarget.of(AuditTarget.USER, user.getId()),
                Map.of("email", email));
        return user;
    }

    @Transactional(readOnly = true)
    public Page<User> list(UUID tenantId, UserStatus status, Pageable pageable) {
        return status == null
                ? users.findAllByTenantId(tenantId, pageable)
                : users.findAllByTenantIdAndStatus(tenantId, status, pageable);
    }

    @Transactional(readOnly = true)
    public User get(UUID tenantId, UUID userId) {
        return users.findByIdAndTenantId(userId, tenantId).orElseThrow(() -> new NotFoundException("User", userId));
    }

    @Transactional
    public User update(UUID tenantId, UUID userId, UpdateUserRequest request) {
        User user = get(tenantId, userId);
        Map<String, Object> changes = new HashMap<>();
        if (request.fullName() != null && !request.fullName().trim().equals(user.getFullName())) {
            user.rename(request.fullName().trim());
            changes.put("fullName", user.getFullName());
        }
        if (request.status() != null && request.status() != user.getStatus()) {
            if (request.status() == UserStatus.INACTIVE && userId.equals(CurrentActor.userId())) {
                throw new ConflictException("You cannot deactivate your own account");
            }
            administrationPolicy.requireCanAdminister(tenantId, userId);
            user.changeStatus(request.status());
            changes.put("status", user.getStatus());
            if (!user.isActive()) {
                events.publishEvent(new UserCredentialsRevokedEvent(userId));
            }
        }
        if (!changes.isEmpty()) {
            audit.record(tenantId, AuditAction.USER_UPDATED, AuditTarget.of(AuditTarget.USER, userId), changes);
        }
        return user;
    }

    /** An administrator sets a new password; the user's sessions end and any lock is lifted. */
    @Transactional
    public void resetPassword(UUID tenantId, UUID userId, String newPassword) {
        PasswordPolicy.validate(newPassword);
        User user = get(tenantId, userId);
        administrationPolicy.requireCanAdminister(tenantId, userId);
        user.changePassword(passwordEncoder.encode(newPassword));
        events.publishEvent(new UserCredentialsRevokedEvent(userId));
        audit.record(tenantId, AuditAction.USER_PASSWORD_RESET, AuditTarget.of(AuditTarget.USER, userId), null);
    }
}
