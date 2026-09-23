package com.codgo.ulock.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    private Instant lastLoginAt;

    private int failedLoginCount;

    private Instant failedLoginWindowStart;

    private Instant lockedUntil;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected User() {}

    public User(UUID tenantId, String email, String passwordHash, String fullName) {
        this.tenantId = tenantId;
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.status = UserStatus.ACTIVE;
    }

    /** Emails are unique per tenant, case-insensitively. */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Counts a failed login within a fixed window that opens at the first failure. Reaching
     * {@code maxFailures} inside the window locks the account for {@code lockDuration}.
     *
     * @return true if this failure locked the account
     */
    public boolean registerFailedLogin(Instant now, int maxFailures, Duration window, Duration lockDuration) {
        if (failedLoginWindowStart == null || !now.isBefore(failedLoginWindowStart.plus(window))) {
            failedLoginWindowStart = now;
            failedLoginCount = 0;
        }
        failedLoginCount++;
        if (failedLoginCount < maxFailures) {
            return false;
        }
        lockedUntil = now.plus(lockDuration);
        clearFailedLogins();
        return true;
    }

    public void registerSuccessfulLogin(Instant now) {
        lastLoginAt = now;
        clearFailedLogins();
    }

    /** Sets a new password and lifts any lock. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.lockedUntil = null;
        clearFailedLogins();
    }

    void rename(String fullName) {
        this.fullName = fullName;
    }

    void changeStatus(UserStatus status) {
        this.status = status;
    }

    private void clearFailedLogins() {
        failedLoginCount = 0;
        failedLoginWindowStart = null;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public UserStatus getStatus() { return status; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public int getFailedLoginCount() { return failedLoginCount; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
