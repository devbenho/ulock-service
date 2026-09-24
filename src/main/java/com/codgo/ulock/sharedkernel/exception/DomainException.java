package com.codgo.ulock.sharedkernel.exception;

/**
 * A violated business rule. The category tells adapters how to report it (e.g. the HTTP status)
 * without the domain knowing about transports.
 */
public abstract class DomainException extends RuntimeException {

    public enum Category {
        NOT_FOUND,
        CONFLICT,
        INVALID,
        FORBIDDEN
    }

    private final Category category;

    protected DomainException(Category category, String message) {
        super(message);
        this.category = category;
    }

    public Category category() {
        return category;
    }
}
