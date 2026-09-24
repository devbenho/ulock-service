package com.codgo.ulock.sharedkernel.event;

import java.time.Instant;

/** Something that happened in a domain. Events are immutable facts named in the past tense. */
public interface DomainEvent {

    Instant occurredAt();
}
