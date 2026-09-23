package com.codgo.ulock.tenant;

import com.codgo.ulock.common.web.PageResponse;
import com.codgo.ulock.tenant.TenantDtos.CreateTenantRequest;
import com.codgo.ulock.tenant.TenantDtos.TenantResponse;
import com.codgo.ulock.tenant.TenantDtos.UpdateTenantRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
class TenantController {

    private final TenantService tenantService;

    TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('tenant:write')")
    ResponseEntity<TenantResponse> create(@Valid @RequestBody CreateTenantRequest request) {
        Tenant tenant = tenantService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/tenants/" + tenant.getId()))
                .body(TenantResponse.from(tenant));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('tenant:read')")
    PageResponse<TenantResponse> list(@RequestParam(required = false) TenantStatus status,
                                      @SortDefault(sort = {"name", "id"}) Pageable pageable) {
        return PageResponse.of(tenantService.list(status, pageable), TenantResponse::from);
    }

    /** Platform admins can read any tenant; every other user can read only their own. */
    @GetMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('tenant:read') or @tenantAccess.isOwnTenant(#tenantId)")
    TenantResponse get(@PathVariable UUID tenantId) {
        return TenantResponse.from(tenantService.get(tenantId));
    }

    @PatchMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('tenant:write')")
    TenantResponse update(@PathVariable UUID tenantId, @Valid @RequestBody UpdateTenantRequest request) {
        return TenantResponse.from(tenantService.update(tenantId, request));
    }
}
