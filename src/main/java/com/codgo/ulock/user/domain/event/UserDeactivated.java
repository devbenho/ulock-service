package com.codgo.ulock.user.domain.event;

import com.codgo.ulock.sharedkernel.event.DomainEvent;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import java.time.Instant;

public record UserDeactivated(UserId userId, TenantId tenantId, Instant occurredAt) implements DomainEvent {}
