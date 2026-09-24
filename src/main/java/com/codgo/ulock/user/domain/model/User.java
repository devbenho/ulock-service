package com.codgo.ulock.user.domain.model;

import com.codgo.ulock.sharedkernel.event.DomainEvent;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.domain.event.UserActivated;
import com.codgo.ulock.user.domain.event.UserCreated;
import com.codgo.ulock.user.domain.event.UserDeactivated;
import com.codgo.ulock.user.domain.event.UserPasswordReset;
import com.codgo.ulock.user.domain.event.UserRenamed;
import com.codgo.ulock.user.domain.exception.InvalidUserException;
import com.codgo.ulock.user.domain.exception.InvalidUserStateException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A person who can log in to one tenant. This is the aggregate root of the user slice. Every state change
 * goes through a method here, which enforces the rules and records the resulting domain events.
 * <p>The email is the login identifier within the tenant and is immutable. v1 has no requirement to
 * change it, and allowing changes would need re-verification and uniqueness handling. To "change" an
 * email, create a new user and deactivate the old one.
 */
public final class User {

    public static final int MAX_NAME_LENGTH = 200;
    /** Lockout rule from the ERD: 5 failed logins within 15 minutes lock the account for 15 minutes. */
    public static final int MAX_FAILED_LOGINS = 5;
    public static final Duration FAILED_LOGIN_WINDOW = Duration.ofMinutes(15);
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserId id;
    private final TenantId tenantId;
    private final Email email;
    private String passwordHash;
    private String fullName;
    private UserStatus status;
    private Instant lastLoginAt;
    private int failedLoginCount;
    private Instant failedLoginWindowStart;
    private Instant lockedUntil;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private User(UserId id, TenantId tenantId, Email email, String passwordHash, String fullName, UserStatus status,
                 Instant lastLoginAt, int failedLoginCount, Instant failedLoginWindowStart, Instant lockedUntil,
                 Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.fullName = fullName;
        this.status = Objects.requireNonNull(status, "status");
        this.lastLoginAt = lastLoginAt;
        this.failedLoginCount = failedLoginCount;
        this.failedLoginWindowStart = failedLoginWindowStart;
        this.lockedUntil = lockedUntil;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** A new, ACTIVE user. Uniqueness of the email within the tenant is checked by the caller. */
    public static User register(TenantId tenantId, Email email, String passwordHash, String fullName, Instant now) {
        User user = new User(UserId.newId(), tenantId, email, passwordHash, validName(fullName), UserStatus.ACTIVE,
                null, 0, null, null, now, now);
        user.domainEvents.add(new UserCreated(user.id, tenantId, email, now));
        return user;
    }

    /** Rebuilds a stored user. For the persistence adapter only; records no events. */
    public static User restore(UserId id, TenantId tenantId, Email email, String passwordHash, String fullName,
                               UserStatus status, Instant lastLoginAt, int failedLoginCount,
                               Instant failedLoginWindowStart, Instant lockedUntil, Instant createdAt,
                               Instant updatedAt) {
        return new User(id, tenantId, email, passwordHash, fullName, status, lastLoginAt, failedLoginCount,
                failedLoginWindowStart, lockedUntil, createdAt, updatedAt);
    }

    // --- administration -----------------------------------------------------------------------

    /** @return true if the name changed */
    public boolean rename(String newFullName, Instant now) {
        String name = validName(newFullName);
        if (name.equals(fullName)) {
            return false;
        }
        fullName = name;
        touch(now);
        domainEvents.add(new UserRenamed(id, tenantId, name, now));
        return true;
    }

    /**
     * ACTIVE → INACTIVE. A deactivated user stays inactive until {@link #activate} is called.
     *
     * @param actor who is deactivating, if a user: nobody may deactivate themselves
     * @return true if the status changed
     */
    public boolean deactivate(UserId actor, Instant now) {
        if (id.equals(actor)) {
            throw new InvalidUserStateException("You cannot deactivate your own account");
        }
        if (!changeStatus(UserStatus.INACTIVE, now)) {
            return false;
        }
        domainEvents.add(new UserDeactivated(id, tenantId, now));
        return true;
    }

    /** INACTIVE → ACTIVE. @return true if the status changed */
    public boolean activate(Instant now) {
        if (!changeStatus(UserStatus.ACTIVE, now)) {
            return false;
        }
        domainEvents.add(new UserActivated(id, tenantId, now));
        return true;
    }

    /** An administrator sets a new password; any lock is lifted. */
    public void resetPassword(String newPasswordHash, Instant now) {
        passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        lockedUntil = null;
        clearFailedLogins();
        touch(now);
        domainEvents.add(new UserPasswordReset(id, tenantId, now));
    }

    // --- authentication -----------------------------------------------------------------------

    /** Whether a password check is worth doing: the user is ACTIVE and not locked. */
    public boolean canAttemptLogin(Instant now) {
        return isActive() && !isLocked(now);
    }

    /**
     * Applies the lockout rule to one login attempt. Failures count within a fixed window that opens
     * at the first failure. The {@link #MAX_FAILED_LOGINS}th failure inside it locks the account.
     */
    public LoginAttempt recordLoginAttempt(boolean passwordMatched, Instant now) {
        if (!isActive()) {
            return new LoginAttempt(LoginAttempt.Result.INACTIVE, false);
        }
        if (isLocked(now)) {
            return new LoginAttempt(LoginAttempt.Result.LOCKED, false);
        }
        touch(now);
        if (passwordMatched) {
            lastLoginAt = now;
            clearFailedLogins();
            return new LoginAttempt(LoginAttempt.Result.SUCCEEDED, false);
        }
        if (failedLoginWindowStart == null || !now.isBefore(failedLoginWindowStart.plus(FAILED_LOGIN_WINDOW))) {
            failedLoginWindowStart = now;
            failedLoginCount = 0;
        }
        failedLoginCount++;
        boolean lockedNow = failedLoginCount >= MAX_FAILED_LOGINS;
        if (lockedNow) {
            lockedUntil = now.plus(LOCK_DURATION);
            clearFailedLogins();
        }
        return new LoginAttempt(LoginAttempt.Result.BAD_PASSWORD, lockedNow);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Returns and clears the events recorded since the last call. */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    /** @return false if the user already has {@code target}; throws if the transition is not allowed */
    private boolean changeStatus(UserStatus target, Instant now) {
        if (status == target) {
            return false;
        }
        if (!status.canTransitionTo(target)) {
            throw new InvalidUserStateException("A user cannot go from " + status + " to " + target);
        }
        status = target;
        touch(now);
        return true;
    }

    private void clearFailedLogins() {
        failedLoginCount = 0;
        failedLoginWindowStart = null;
    }

    private void touch(Instant now) {
        updatedAt = now;
    }

    private static String validName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidUserException("Full name must be 1 to " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    public UserId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public Email email() { return email; }
    public String passwordHash() { return passwordHash; }
    public String fullName() { return fullName; }
    public UserStatus status() { return status; }
    public Instant lastLoginAt() { return lastLoginAt; }
    public int failedLoginCount() { return failedLoginCount; }
    public Instant failedLoginWindowStart() { return failedLoginWindowStart; }
    public Instant lockedUntil() { return lockedUntil; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
