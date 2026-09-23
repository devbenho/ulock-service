package com.codgo.ulock.user;

import java.util.UUID;

/** Published when a user's existing sessions must end: deactivation or a password reset. */
public record UserCredentialsRevokedEvent(UUID userId) {}
