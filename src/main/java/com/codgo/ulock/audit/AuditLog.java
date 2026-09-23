package com.codgo.ulock.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only audit record; the database rejects updates and deletes. */
@Entity
@Immutable
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID tenantId;

    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    private String targetType;

    private String targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> details;

    private String ipAddress;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {}

    AuditLog(UUID tenantId, UUID actorUserId, AuditAction action, AuditTarget target,
             Map<String, Object> details, String ipAddress) {
        this.tenantId = tenantId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetType = target == null ? null : target.type();
        this.targetId = target == null ? null : target.id();
        this.details = details;
        this.ipAddress = ipAddress;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getActorUserId() { return actorUserId; }
    public AuditAction getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public Map<String, Object> getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
}
