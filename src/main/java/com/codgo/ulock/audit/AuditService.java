package com.codgo.ulock.audit;

import com.codgo.ulock.common.PlatformTenant;
import com.codgo.ulock.common.security.CurrentActor;
import com.codgo.ulock.common.security.TenantAccessDeniedEvent;
import com.codgo.ulock.common.web.ClientIp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit records. Records join the caller's transaction, so an audited change and its audit
 * entry commit or roll back together.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Records an action performed by the currently authenticated user. */
    @Transactional
    public void record(UUID tenantId, AuditAction action, AuditTarget target, Map<String, Object> details) {
        recordAs(CurrentActor.userId(), tenantId, action, target, details);
    }

    /** Records an action on behalf of an explicit actor, e.g. a user who is not yet authenticated. */
    @Transactional
    public void recordAs(UUID actorUserId, UUID tenantId, AuditAction action, AuditTarget target,
                         Map<String, Object> details) {
        repository.save(new AuditLog(tenantId, actorUserId, action, target, details, ClientIp.current()));
    }

    @Transactional(readOnly = true)
    Page<AuditLog> search(UUID tenantId, AuditLogFilter filter, Pageable pageable) {
        return repository.findAll(filter.toSpecification(tenantId), pageable);
    }

    /**
     * Cross-tenant attempts are logged against the caller's own tenant, which is known to exist;
     * machine clients are platform-level, so theirs go to the platform tenant.
     */
    @EventListener
    @Transactional
    public void onTenantAccessDenied(TenantAccessDeniedEvent event) {
        Map<String, Object> details = new HashMap<>();
        details.put("method", event.method());
        details.put("path", event.path());
        if (event.actor().clientId() != null) {
            details.put("clientId", event.actor().clientId());
        }
        UUID tenantId = event.actor().tenantId() != null ? event.actor().tenantId() : PlatformTenant.ID;
        repository.save(new AuditLog(
                tenantId,
                event.actor().userId(),
                AuditAction.TENANT_ACCESS_DENIED,
                AuditTarget.of(AuditTarget.TENANT, event.requestedTenantId()),
                details,
                event.ipAddress()));
    }
}
