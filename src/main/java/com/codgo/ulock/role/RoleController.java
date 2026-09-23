package com.codgo.ulock.role;

import com.codgo.ulock.common.web.PageResponse;
import com.codgo.ulock.role.RoleDtos.CreateRoleRequest;
import com.codgo.ulock.role.RoleDtos.ReplacePermissionsRequest;
import com.codgo.ulock.role.RoleDtos.RoleMemberResponse;
import com.codgo.ulock.role.RoleDtos.RoleResponse;
import com.codgo.ulock.role.RoleDtos.UpdateRoleRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/roles")
class RoleController {

    private final RoleService roleService;

    RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('role:write')")
    ResponseEntity<RoleResponse> create(@PathVariable UUID tenantId, @Valid @RequestBody CreateRoleRequest request) {
        RoleResponse role = roleService.create(tenantId, request);
        return ResponseEntity.created(URI.create("/api/v1/tenants/" + tenantId + "/roles/" + role.id())).body(role);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('role:read')")
    PageResponse<RoleResponse> list(@PathVariable UUID tenantId, @SortDefault(sort = {"name", "id"}) Pageable pageable) {
        return PageResponse.of(roleService.list(tenantId, pageable), Function.identity());
    }

    @GetMapping("/{roleId}")
    @PreAuthorize("hasAuthority('role:read')")
    RoleResponse get(@PathVariable UUID tenantId, @PathVariable UUID roleId) {
        return roleService.get(tenantId, roleId);
    }

    @PatchMapping("/{roleId}")
    @PreAuthorize("hasAuthority('role:write')")
    RoleResponse update(@PathVariable UUID tenantId, @PathVariable UUID roleId,
                        @Valid @RequestBody UpdateRoleRequest request) {
        return roleService.update(tenantId, roleId, request);
    }

    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasAuthority('role:write')")
    ResponseEntity<Void> delete(@PathVariable UUID tenantId, @PathVariable UUID roleId) {
        roleService.delete(tenantId, roleId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('role:write')")
    RoleResponse replacePermissions(@PathVariable UUID tenantId, @PathVariable UUID roleId,
                                    @Valid @RequestBody ReplacePermissionsRequest request) {
        return roleService.replacePermissions(tenantId, roleId, request.permissionIds());
    }

    @GetMapping("/{roleId}/members")
    @PreAuthorize("hasAuthority('role:read')")
    PageResponse<RoleMemberResponse> members(@PathVariable UUID tenantId, @PathVariable UUID roleId,
                                             @SortDefault(sort = {"email", "id"}) Pageable pageable) {
        return PageResponse.of(roleService.members(tenantId, roleId, pageable), Function.identity());
    }
}
