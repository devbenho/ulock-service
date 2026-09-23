package com.codgo.ulock.auth;

import com.codgo.ulock.auth.AuthDtos.AuthzCheckRequest;
import com.codgo.ulock.auth.AuthDtos.AuthzCheckResponse;
import com.codgo.ulock.auth.AuthDtos.Decision;
import com.codgo.ulock.role.AccessService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The hot-path check other services call: does this user hold this permission in this tenant? */
@RestController
@RequestMapping("/api/v1/authz")
class AuthzController {

    private final AccessService accessService;

    AuthzController(AccessService accessService) {
        this.accessService = accessService;
    }

    @PostMapping("/check")
    @PreAuthorize("hasAuthority('SCOPE_authz:check')")
    AuthzCheckResponse check(@Valid @RequestBody AuthzCheckRequest request) {
        boolean allowed = accessService.hasPermission(request.tenantId(), request.userId(), request.permission());
        return new AuthzCheckResponse(allowed ? Decision.ALLOW : Decision.DENY);
    }
}
