package com.codgo.ulock.role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Null for built-in system permissions. */
    @Column(updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private String code;

    private String description;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected Permission() {}

    /**
     * Permission codes are canonical as given, with no trimming or case folding: lower-case
     * {@code resource:action} segments, e.g. {@code invoice:approve}. A code is unique within its
     * tenant and may not reuse a system permission's code.
     */
    public static final String CODE_PATTERN = "^[a-z][a-z0-9_-]*(:[a-z][a-z0-9_-]*)+$";
    public static final int MAX_CODE_LENGTH = 100;

    Permission(UUID tenantId, String code, String description) {
        if (code == null || code.length() > MAX_CODE_LENGTH || !code.matches(CODE_PATTERN)) {
            throw new IllegalArgumentException("Invalid permission code: " + code);
        }
        this.tenantId = tenantId;
        this.code = code;
        this.description = description;
    }

    public boolean isSystem() {
        return tenantId == null;
    }

    public boolean isPlatformOnly() {
        return isSystem() && SystemPermission.platformOnlyCodes().contains(code);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
