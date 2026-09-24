package com.codgo.ulock.common.event;

import com.codgo.ulock.sharedkernel.event.DomainEvent;
import com.codgo.ulock.sharedkernel.event.DomainEventPublisherPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Delivers domain events to Spring {@code @EventListener}s, synchronously and in the caller's transaction. */
@Component
class SpringDomainEventPublisher implements DomainEventPublisherPort {

    private final ApplicationEventPublisher publisher;

    SpringDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(DomainEvent event) {
        publisher.publishEvent(event);
    }
}
