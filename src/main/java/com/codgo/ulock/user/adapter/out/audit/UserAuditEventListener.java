package com.codgo.ulock.user.adapter.out.audit;

import com.codgo.ulock.audit.AuditAction;
import com.codgo.ulock.audit.AuditService;
import com.codgo.ulock.audit.AuditTarget;
import com.codgo.ulock.user.domain.event.UserActivated;
import com.codgo.ulock.user.domain.event.UserCreated;
import com.codgo.ulock.user.domain.event.UserDeactivated;
import com.codgo.ulock.user.domain.event.UserPasswordReset;
import com.codgo.ulock.user.domain.event.UserUpdated;
import com.codgo.ulock.user.domain.model.UserStatus;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Writes the audit trail for user changes. Runs synchronously, so entries commit with the change. */
@Component
class UserAuditEventListener {

    private final AuditService audit;

    UserAuditEventListener(AuditService audit) {
        this.audit = audit;
    }

    @EventListener
    void on(UserCreated event) {
        record(event.tenantId().value(), AuditAction.USER_CREATED, event.userId().value(),
                Map.of("email", event.email().value()));
    }

    @EventListener
    void on(UserUpdated event) {
        record(event.tenantId().value(), AuditAction.USER_UPDATED, event.userId().value(),
                Map.of("fullName", event.fullName()));
    }

    @EventListener
    void on(UserDeactivated event) {
        record(event.tenantId().value(), AuditAction.USER_UPDATED, event.userId().value(),
                Map.of("status", UserStatus.INACTIVE));
    }

    @EventListener
    void on(UserActivated event) {
        record(event.tenantId().value(), AuditAction.USER_UPDATED, event.userId().value(),
                Map.of("status", UserStatus.ACTIVE));
    }

    @EventListener
    void on(UserPasswordReset event) {
        record(event.tenantId().value(), AuditAction.USER_PASSWORD_RESET, event.userId().value(), null);
    }

    private void record(UUID tenantId, AuditAction action, UUID userId, Map<String, Object> details) {
        audit.record(tenantId, action, AuditTarget.of(AuditTarget.USER, userId), details);
    }
}
