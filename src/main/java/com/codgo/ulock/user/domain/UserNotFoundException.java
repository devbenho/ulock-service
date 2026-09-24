package com.codgo.ulock.user.domain;

import com.codgo.ulock.sharedkernel.exception.DomainException;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.domain.User;

public class UserNotFoundException extends DomainException {

    public UserNotFoundException(UserId userId) {
        super(Category.NOT_FOUND, "User " + userId + " was not found");
    }
}
