package com.codgo.ulock.sharedkernel.event;

import java.util.Collection;

/**
 * Outbound port through which every slice's application layer publishes events. Listeners run
 * synchronously in the publisher's transaction.
 */
public interface DomainEventPublisherPort {

    void publish(DomainEvent event);

    default void publishAll(Collection<? extends DomainEvent> events) {
        events.forEach(this::publish);
    }
}
