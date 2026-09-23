package com.codgo.ulock.audit;

import com.codgo.ulock.common.error.InvalidRequestException;
import com.codgo.ulock.common.web.PageResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/audit-logs")
class AuditLogController {

    private final AuditService auditService;

    AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    PageResponse<AuditLogResponse> list(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @SortDefault(sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new InvalidRequestException("'from' must be before 'to'");
        }
        var filter = new AuditLogFilter(userId, action, from, to);
        return PageResponse.of(auditService.search(tenantId, filter, pageable), AuditLogResponse::from);
    }
}
