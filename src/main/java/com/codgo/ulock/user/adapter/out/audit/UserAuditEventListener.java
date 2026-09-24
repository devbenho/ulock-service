package com.codgo.ulock.user.adapter.out.audit;

import com.codgo.ulock.audit.AuditAction;
import com.codgo.ulock.audit.AuditService;
import com.codgo.ulock.audit.AuditTarget;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.domain.event.UserActivated;
import com.codgo.ulock.user.domain.event.UserCreated;
import com.codgo.ulock.user.domain.event.UserDeactivated;
import com.codgo.ulock.user.domain.event.UserPasswordReset;
import com.codgo.ulock.user.domain.event.UserRenamed;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Writes the audit trail for user changes. Runs synchronously inside the publishing transaction, so an
 * audit record commits or rolls back together with the change it records.
 */
@Component
class UserAuditEventListener {

    private final AuditService audit;

    UserAuditEventListener(AuditService audit) {
        this.audit = audit;
    }

    @EventListener
    void on(UserCreated event) {
        record(event.tenantId(), AuditAction.USER_CREATED, event.userId(), Map.of("email", event.email().value()));
    }

    @EventListener
    void on(UserRenamed event) {
        record(event.tenantId(), AuditAction.USER_RENAMED, event.userId(), Map.of("fullName", event.fullName()));
    }

    @EventListener
    void on(UserDeactivated event) {
        record(event.tenantId(), AuditAction.USER_DEACTIVATED, event.userId(), null);
    }

    @EventListener
    void on(UserActivated event) {
        record(event.tenantId(), AuditAction.USER_ACTIVATED, event.userId(), null);
    }

    @EventListener
    void on(UserPasswordReset event) {
        record(event.tenantId(), AuditAction.USER_PASSWORD_RESET, event.userId(), null);
    }

    private void record(TenantId tenantId, AuditAction action, UserId userId, Map<String, Object> details) {
        audit.record(tenantId.value(), action, AuditTarget.of(AuditTarget.USER, userId.value()), details);
    }
}
