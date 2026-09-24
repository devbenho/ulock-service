package com.codgo.ulock.user.domain;

import com.codgo.ulock.sharedkernel.event.DomainEvent;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import java.time.Instant;

public record UserPasswordReset(UserId userId, TenantId tenantId, Instant occurredAt) implements DomainEvent {}
