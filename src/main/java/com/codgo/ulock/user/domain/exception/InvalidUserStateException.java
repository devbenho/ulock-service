package com.codgo.ulock.user.domain.exception;

import com.codgo.ulock.sharedkernel.exception.DomainException;

/** An operation that the user's current state does not allow. */
public class InvalidUserStateException extends DomainException {

    public InvalidUserStateException(String message) {
        super(Category.CONFLICT, message);
    }
}
