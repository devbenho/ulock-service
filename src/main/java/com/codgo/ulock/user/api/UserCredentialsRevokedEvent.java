package com.codgo.ulock.user.api;

import com.codgo.ulock.sharedkernel.event.DomainEvent;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import java.time.Instant;

/**
 * Published contract: the user's existing sessions must end (deactivation or a password reset).
 * Other slices listen for this instead of the user slice's internal domain events.
 */
public record UserCredentialsRevokedEvent(UserId userId, Instant occurredAt) implements DomainEvent {}
