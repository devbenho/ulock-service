package com.codgo.ulock.user.web;

import static com.codgo.ulock.common.web.ValidationPatterns.NOT_BLANK;

import com.codgo.ulock.user.domain.UserStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial update: null fields are left unchanged. */
public record UpdateUserRequest(
        @Size(min = 1, max = 200) @Pattern(regexp = NOT_BLANK, message = "must not be blank") String fullName,
        UserStatus status) {}
