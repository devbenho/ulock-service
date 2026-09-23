package com.codgo.ulock.role;

import com.codgo.ulock.role.RoleDtos.AccessSummaryResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Role assignments and access summaries for a single user. */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/users/{userId}")
class UserAccessController {

    private final RoleAssignmentService assignments;
    private final AccessService accessService;

    UserAccessController(RoleAssignmentService assignments, AccessService accessService) {
        this.assignments = assignments;
        this.accessService = accessService;
    }

    @PostMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('role:write')")
    ResponseEntity<Void> assign(@PathVariable UUID tenantId, @PathVariable UUID userId, @PathVariable UUID roleId) {
        assignments.assign(tenantId, userId, roleId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('role:write')")
    ResponseEntity<Void> revoke(@PathVariable UUID tenantId, @PathVariable UUID userId, @PathVariable UUID roleId) {
        assignments.revoke(tenantId, userId, roleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/access-summary")
    @PreAuthorize("hasAuthority('role:read')")
    AccessSummaryResponse accessSummary(@PathVariable UUID tenantId, @PathVariable UUID userId) {
        return accessService.summarize(tenantId, userId);
    }
}
