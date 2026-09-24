package com.codgo.ulock.user.adapter.in.web;

import com.codgo.ulock.common.security.CurrentActor;
import com.codgo.ulock.common.web.PageQueries;
import com.codgo.ulock.common.web.PageResponse;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.adapter.in.web.dto.CreateUserRequest;
import com.codgo.ulock.user.adapter.in.web.dto.ResetPasswordRequest;
import com.codgo.ulock.user.adapter.in.web.dto.UpdateUserRequest;
import com.codgo.ulock.user.adapter.in.web.dto.UserResponse;
import com.codgo.ulock.user.application.port.in.command.CreateUserCommand;
import com.codgo.ulock.user.application.port.in.model.UserView;
import com.codgo.ulock.user.application.service.UserQueryService;
import com.codgo.ulock.user.application.service.UserService;
import com.codgo.ulock.user.application.service.command.UpdateUserCommand;
import com.codgo.ulock.user.domain.model.UserStatus;
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

/** Writes go to {@link UserService}, reads to {@link UserQueryService}. */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/users")
class UserController {

    private final UserService userService;
    private final UserQueryService userQueries;
    private final Clock clock;

    UserController(UserService userService, UserQueryService userQueries, Clock clock) {
        this.userService = userService;
        this.userQueries = userQueries;
        this.clock = clock;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:write')")
    ResponseEntity<UserResponse> create(@PathVariable UUID tenantId, @Valid @RequestBody CreateUserRequest request) {
        UserView user = userService.createUser(
                new CreateUserCommand(TenantId.of(tenantId), request.email(), request.fullName(), request.password()));
        return ResponseEntity.created(URI.create("/api/v1/tenants/" + tenantId + "/users/" + user.id()))
                .body(toResponse(user));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    PageResponse<UserResponse> list(@PathVariable UUID tenantId,
                                    @RequestParam(required = false) UserStatus status,
                                    @SortDefault(sort = {"email", "id"}) Pageable pageable) {
        return PageResponse.of(userQueries.list(TenantId.of(tenantId), status, PageQueries.from(pageable)),
                this::toResponse);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('user:read')")
    UserResponse get(@PathVariable UUID tenantId, @PathVariable UUID userId) {
        return toResponse(userQueries.getUser(TenantId.of(tenantId), UserId.of(userId)));
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("hasAuthority('user:write')")
    UserResponse update(@PathVariable UUID tenantId, @PathVariable UUID userId,
                        @Valid @RequestBody UpdateUserRequest request) {
        UUID actor = CurrentActor.userId();
        return toResponse(userService.update(new UpdateUserCommand(TenantId.of(tenantId), UserId.of(userId),
                request.fullName(), request.status(), actor == null ? null : UserId.of(actor))));
    }

    @PostMapping("/{userId}/password-reset")
    @PreAuthorize("hasAuthority('user:write')")
    ResponseEntity<Void> resetPassword(@PathVariable UUID tenantId, @PathVariable UUID userId,
                                       @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(TenantId.of(tenantId), UserId.of(userId), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private UserResponse toResponse(UserView user) {
        return UserResponse.from(user, clock.instant());
    }
}
