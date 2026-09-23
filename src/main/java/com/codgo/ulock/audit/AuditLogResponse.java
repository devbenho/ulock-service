package com.codgo.ulock.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID tenantId,
        UUID actorUserId,
        AuditAction action,
        String targetType,
        String targetId,
        Map<String, Object> details,
        String ipAddress,
        Instant createdAt) {

    static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getTenantId(), log.getActorUserId(), log.getAction(),
                log.getTargetType(), log.getTargetId(), log.getDetails(), log.getIpAddress(), log.getCreatedAt());
    }
}
