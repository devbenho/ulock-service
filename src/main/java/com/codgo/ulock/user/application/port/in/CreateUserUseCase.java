package com.codgo.ulock.user.application.port.in;

import com.codgo.ulock.user.application.port.in.command.CreateUserCommand;
import com.codgo.ulock.user.application.port.in.model.UserView;

/** Creates users on behalf of other slices, e.g. a new tenant's first administrator. */
public interface CreateUserUseCase {

    /** @throws com.codgo.ulock.sharedkernel.exception.DomainException if the password, email or name is invalid, or the email is taken */
    UserView createUser(CreateUserCommand command);
}
