package com.codgo.ulock.user.api;

import com.codgo.ulock.sharedkernel.valueobject.TenantId;

/** {@code email} and {@code password} are raw input; the user slice validates and normalises them. */
public record CreateUserCommand(TenantId tenantId, String email, String fullName, String password) {}
