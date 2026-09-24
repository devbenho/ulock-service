package com.codgo.ulock.user.domain.exception;

import com.codgo.ulock.sharedkernel.exception.DomainException;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.domain.model.User;

public class UserNotFoundException extends DomainException {

    public UserNotFoundException(UserId userId) {
        super(Category.NOT_FOUND, "User " + userId + " was not found");
    }
}
