package com.codgo.ulock.user.application;

import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;

/** An administrator sets a new password for another user. Internal to the slice. */
public record ResetPasswordCommand(TenantId tenantId, UserId userId, String newPassword) {}
