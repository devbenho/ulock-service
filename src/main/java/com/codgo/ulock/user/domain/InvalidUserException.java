package com.codgo.ulock.user.domain;

import com.codgo.ulock.sharedkernel.exception.DomainException;
import com.codgo.ulock.user.domain.User;

/** User data that breaks an invariant, e.g. a blank name. */
public class InvalidUserException extends DomainException {

    public InvalidUserException(String message) {
        super(Category.INVALID, message);
    }
}
