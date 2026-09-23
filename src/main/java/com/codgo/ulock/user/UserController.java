package com.codgo.ulock.user;

import com.codgo.ulock.common.web.PageResponse;
import com.codgo.ulock.user.UserDtos.CreateUserRequest;
import com.codgo.ulock.user.UserDtos.ResetPasswordRequest;
import com.codgo.ulock.user.UserDtos.UpdateUserRequest;
import com.codgo.ulock.user.UserDtos.UserResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
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
@RequestMapping("/api/v1/tenants/{tenantId}/users")
class UserController {

    private final UserService userService;
    private final Clock clock;

    UserController(UserService userService, Clock clock) {
        this.userService = userService;
        this.clock = clock;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:write')")
    ResponseEntity<UserResponse> create(@PathVariable UUID tenantId, @Valid @RequestBody CreateUserRequest request) {
        User user = userService.create(tenantId, request);
        return ResponseEntity.created(URI.create("/api/v1/tenants/" + tenantId + "/users/" + user.getId()))
                .body(toResponse(user));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    PageResponse<UserResponse> list(@PathVariable UUID tenantId,
                                    @RequestParam(required = false) UserStatus status,
                                    @SortDefault(sort = {"email", "id"}) Pageable pageable) {
        return PageResponse.of(userService.list(tenantId, status, pageable), this::toResponse);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('user:read')")
    UserResponse get(@PathVariable UUID tenantId, @PathVariable UUID userId) {
        return toResponse(userService.get(tenantId, userId));
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("hasAuthority('user:write')")
    UserResponse update(@PathVariable UUID tenantId, @PathVariable UUID userId,
                        @Valid @RequestBody UpdateUserRequest request) {
        return toResponse(userService.update(tenantId, userId, request));
    }

    @PostMapping("/{userId}/password-reset")
    @PreAuthorize("hasAuthority('user:write')")
    ResponseEntity<Void> resetPassword(@PathVariable UUID tenantId, @PathVariable UUID userId,
                                       @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(tenantId, userId, request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private UserResponse toResponse(User user) {
        return UserResponse.from(user, clock.instant());
    }
}
