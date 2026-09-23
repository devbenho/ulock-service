package com.codgo.ulock.common.security;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Tenant-isolation predicate, also usable from {@code @PreAuthorize} as {@code @tenantAccess}. */
@Component("tenantAccess")
public class TenantAccess {

    public boolean isOwnTenant(UUID tenantId) {
        return tenantId != null && CurrentActor.find()
                .map(actor -> tenantId.equals(actor.tenantId()))
                .orElse(false);
    }
}
