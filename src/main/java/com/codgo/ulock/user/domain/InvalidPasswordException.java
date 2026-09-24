package com.codgo.ulock.user.domain;

import com.codgo.ulock.sharedkernel.exception.DomainException;

public class InvalidPasswordException extends DomainException {

    public InvalidPasswordException(String message) {
        super(Category.INVALID, message);
    }
}
