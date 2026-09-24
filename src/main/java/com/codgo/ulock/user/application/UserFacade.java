package com.codgo.ulock.user.application;

import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.api.AuthenticateUserCommand;
import com.codgo.ulock.user.api.AuthenticationResult;
import com.codgo.ulock.user.api.CreateUserCommand;
import com.codgo.ulock.user.api.UserApi;
import com.codgo.ulock.user.api.UserView;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Implements the slice's public face by delegating to the handlers. Other slices depend on
 * {@link UserApi}; code inside the slice calls the handlers directly.
 */
@Service
class UserFacade implements UserApi {

    private final CreateUserHandler createUser;
    private final AuthenticateUserHandler authenticateUser;
    private final UserQueries queries;

    UserFacade(CreateUserHandler createUser, AuthenticateUserHandler authenticateUser, UserQueries queries) {
        this.createUser = createUser;
        this.authenticateUser = authenticateUser;
        this.queries = queries;
    }

    @Override
    public UserView createUser(CreateUserCommand command) {
        return createUser.handle(command);
    }

    @Override
    public UserView getUser(TenantId tenantId, UserId userId) {
        return queries.getUser(tenantId, userId);
    }

    @Override
    public Optional<UserView> findUser(TenantId tenantId, UserId userId) {
        return queries.findUser(tenantId, userId);
    }

    @Override
    public Optional<UserView> findUserByEmail(TenantId tenantId, String email) {
        return queries.findUserByEmail(tenantId, email);
    }

    @Override
    public boolean hasUsers(TenantId tenantId) {
        return queries.hasUsers(tenantId);
    }

    @Override
    public AuthenticationResult authenticate(AuthenticateUserCommand command) {
        return authenticateUser.handle(command);
    }
}
