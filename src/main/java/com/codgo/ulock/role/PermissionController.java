package com.codgo.ulock.role;

import com.codgo.ulock.common.web.PageResponse;
import com.codgo.ulock.role.RoleDtos.CreatePermissionRequest;
import com.codgo.ulock.role.RoleDtos.PermissionResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/permissions")
class PermissionController {

    private final PermissionService permissionService;

    PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('role:write')")
    ResponseEntity<PermissionResponse> create(@PathVariable UUID tenantId,
                                              @Valid @RequestBody CreatePermissionRequest request) {
        PermissionResponse permission = PermissionResponse.from(permissionService.create(tenantId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(permission);
    }

    /** Tenant-defined permissions together with the system permissions the tenant may use. */
    @GetMapping
    @PreAuthorize("hasAuthority('role:read')")
    PageResponse<PermissionResponse> list(@PathVariable UUID tenantId,
                                          @SortDefault(sort = {"code", "id"}) Pageable pageable) {
        return PageResponse.of(permissionService.list(tenantId, pageable), PermissionResponse::from);
    }
}
