package com.codgo.ulock.common.security;

import com.codgo.ulock.common.error.ForbiddenException;
import com.codgo.ulock.common.error.InvalidRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Enforces that the token's {@code tid} claim matches the {@code {tenantId}} path variable. Mismatches
 * return 403 and are published for auditing.
 * <ul>
 *   <li>Tenant-scoped resources below {@code /tenants/{tenantId}/...}: no exceptions, platform admins included.</li>
 *   <li>The tenant resource {@code /tenants/{tenantId}} itself: platform admins manage every tenant.</li>
 * </ul>
 */
@Component
public class TenantIsolationInterceptor implements HandlerInterceptor {

    public static final String TENANT_RESOURCE_PATTERN = "/api/v1/tenants/*";
    public static final String TENANT_SCOPED_PATTERN = "/api/v1/tenants/*/*/**";
    static final String DENIED = "Access to this tenant is not allowed";

    private static final String TENANT_RESOURCE_TEMPLATE = "/api/v1/tenants/{tenantId}";
    /** Platform-only system permissions (see SystemPermission) that manage tenants themselves. */
    private static final Set<String> TENANT_MANAGEMENT_AUTHORITIES = Set.of("tenant:read", "tenant:write");

    private final TenantAccess tenantAccess;
    private final ApplicationEventPublisher events;

    public TenantIsolationInterceptor(TenantAccess tenantAccess, ApplicationEventPublisher events) {
        this.tenantAccess = tenantAccess;
        this.events = events;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (!(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables)
                || !(variables.get("tenantId") instanceof String raw)) {
            // Unmatched paths fall through to a 404; a controller under /tenants/* must name its variable tenantId.
            if (handler instanceof HandlerMethod) {
                throw new ForbiddenException(DENIED);
            }
            return true;
        }
        UUID requestedTenantId = parseLikeTheArgumentBinder(raw);
        if (tenantAccess.isOwnTenant(requestedTenantId) || isPlatformTenantManagement(request)) {
            return true;
        }
        CurrentActor.find().ifPresent(actor -> events.publishEvent(new TenantAccessDeniedEvent(
                actor, requestedTenantId, request.getMethod(), request.getRequestURI(), request.getRemoteAddr())));
        throw new ForbiddenException(DENIED);
    }

    /**
     * Must resolve exactly the UUID the controller will receive. Spring's String-to-UUID conversion
     * trims whitespace, so a stricter parse here would let "%20&lt;uuid&gt;" pass as unparseable.
     * Anything unparseable fails closed.
     */
    private static UUID parseLikeTheArgumentBinder(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException malformed) {
            throw new InvalidRequestException("Invalid tenant id");
        }
    }

    private static boolean isPlatformTenantManagement(HttpServletRequest request) {
        return TENANT_RESOURCE_TEMPLATE.equals(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
                && CurrentActor.authorities().stream().anyMatch(TENANT_MANAGEMENT_AUTHORITIES::contains);
    }
}
