package com.codgo.ulock.user.api;

import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import java.util.Optional;

/**
 * The user slice's public face: the only part of the slice other slices may use. Every lookup is
 * scoped to one tenant, so a user is never visible through another tenant's id.
 */
public interface UserApi {

    /** @throws com.codgo.ulock.sharedkernel.exception.DomainException if the password, email or name is invalid, or the email is taken */
    UserView createUser(CreateUserCommand command);

    /** @throws com.codgo.ulock.sharedkernel.exception.DomainException (NOT_FOUND) if the tenant has no such user */
    UserView getUser(TenantId tenantId, UserId userId);

    Optional<UserView> findUser(TenantId tenantId, UserId userId);

    /** Empty when no user matches, including when {@code email} is not a valid address. */
    Optional<UserView> findUserByEmail(TenantId tenantId, String email);

    boolean hasUsers(TenantId tenantId);

    /** Never throws for bad credentials; failures come back as a result so their side effects commit. */
    AuthenticationResult authenticate(AuthenticateUserCommand command);
}
