package com.codgo.ulock.user.application.port.in.model;

import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;

/**
 * Read model of a user for other slices. {@code status} is {@code ACTIVE} or {@code INACTIVE}.
 * It is a string so other slices never depend on the user domain.
 */
public record UserView(UserId id, TenantId tenantId, Email email, String fullName, String status, boolean active) {}
