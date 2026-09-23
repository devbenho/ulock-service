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

    Permission(UUID tenantId, String code, String description) {
        this.tenantId = tenantId;
        this.code = code;
        this.description = description;
    }

    public boolean isSystem() {
        return tenantId == null;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
