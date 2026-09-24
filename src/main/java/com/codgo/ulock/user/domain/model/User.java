package com.codgo.ulock.user.domain.model;

import com.codgo.ulock.sharedkernel.event.DomainEvent;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.domain.event.UserActivated;
import com.codgo.ulock.user.domain.event.UserCreated;
import com.codgo.ulock.user.domain.event.UserDeactivated;
import com.codgo.ulock.user.domain.event.UserPasswordReset;
import com.codgo.ulock.user.domain.event.UserUpdated;
import com.codgo.ulock.user.domain.exception.InvalidUserException;
import com.codgo.ulock.user.domain.exception.InvalidUserStateException;
import com.codgo.ulock.user.domain.policy.LockoutPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A person who can log in to one tenant. Aggregate root of the user slice: every state change goes
 * through a method here, which enforces the rules and records the resulting domain events.
 */
public final class User {

    public static final int MAX_NAME_LENGTH = 200;

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

    private User(UserSnapshot s) {
        this.id = Objects.requireNonNull(s.id(), "id");
        this.tenantId = Objects.requireNonNull(s.tenantId(), "tenantId");
        this.email = Objects.requireNonNull(s.email(), "email");
        this.passwordHash = Objects.requireNonNull(s.passwordHash(), "passwordHash");
        this.fullName = s.fullName();
        this.status = Objects.requireNonNull(s.status(), "status");
        this.lastLoginAt = s.lastLoginAt();
        this.failedLoginCount = s.failedLoginCount();
        this.failedLoginWindowStart = s.failedLoginWindowStart();
        this.lockedUntil = s.lockedUntil();
        this.createdAt = s.createdAt();
        this.updatedAt = s.updatedAt();
    }

    /** A new, ACTIVE user. Uniqueness of the email within the tenant is checked by the caller. */
    public static User register(TenantId tenantId, Email email, String passwordHash, String fullName, Instant now) {
        User user = new User(new UserSnapshot(UserId.newId(), tenantId, email, passwordHash, validName(fullName),
                UserStatus.ACTIVE, null, 0, null, null, now, now));
        user.domainEvents.add(new UserCreated(user.id, tenantId, email, now));
        return user;
    }

    public static User fromSnapshot(UserSnapshot snapshot) {
        return new User(snapshot);
    }

    public UserSnapshot toSnapshot() {
        return new UserSnapshot(id, tenantId, email, passwordHash, fullName, status, lastLoginAt, failedLoginCount,
                failedLoginWindowStart, lockedUntil, createdAt, updatedAt);
    }

    /** @return true if the name changed */
    public boolean rename(String newFullName, Instant now) {
        String name = validName(newFullName);
        if (name.equals(fullName)) {
            return false;
        }
        fullName = name;
        touch(now);
        domainEvents.add(new UserUpdated(id, tenantId, name, now));
        return true;
    }

    /**
     * Deactivates the user; a deactivated user stays inactive until {@link #activate} is called.
     *
     * @param actor who is deactivating, if a user: nobody may deactivate themselves
     * @return true if the status changed
     */
    public boolean deactivate(UserId actor, Instant now) {
        if (id.equals(actor)) {
            throw new InvalidUserStateException("You cannot deactivate your own account");
        }
        if (status == UserStatus.INACTIVE) {
            return false;
        }
        status = UserStatus.INACTIVE;
        touch(now);
        domainEvents.add(new UserDeactivated(id, tenantId, now));
        return true;
    }

    /** @return true if the status changed */
    public boolean activate(Instant now) {
        if (status == UserStatus.ACTIVE) {
            return false;
        }
        status = UserStatus.ACTIVE;
        touch(now);
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


    /** Whether a password check is worth doing: the user is ACTIVE and not locked. */
    public boolean canAttemptLogin(Instant now) {
        return isActive() && !isLocked(now);
    }

    /**
     * Applies the lockout rules to one login attempt. Failures count within a fixed window that
     * opens at the first failure; reaching the policy's limit inside it locks the account.
     */
    public LoginAttempt recordLoginAttempt(boolean passwordMatched, LockoutPolicy policy, Instant now) {
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
        if (failedLoginWindowStart == null || !now.isBefore(failedLoginWindowStart.plus(policy.window()))) {
            failedLoginWindowStart = now;
            failedLoginCount = 0;
        }
        failedLoginCount++;
        boolean lockedNow = failedLoginCount >= policy.maxFailures();
        if (lockedNow) {
            lockedUntil = now.plus(policy.lockDuration());
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
    public Instant lockedUntil() { return lockedUntil; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
