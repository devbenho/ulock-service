package com.codgo.ulock.user.domain.event;

import com.codgo.ulock.sharedkernel.event.DomainEvent;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import java.time.Instant;

public record UserCreated(UserId userId, TenantId tenantId, Email email, Instant occurredAt) implements DomainEvent {}
