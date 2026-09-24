package com.codgo.ulock.user.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(@NotBlank String newPassword) {}
