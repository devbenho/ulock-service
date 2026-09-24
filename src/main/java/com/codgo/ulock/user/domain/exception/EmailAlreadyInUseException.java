package com.codgo.ulock.user.domain.exception;

import com.codgo.ulock.sharedkernel.exception.DomainException;

public class EmailAlreadyInUseException extends DomainException {

    public EmailAlreadyInUseException() {
        super(Category.CONFLICT, "A user with this email already exists in the tenant");
    }
}
