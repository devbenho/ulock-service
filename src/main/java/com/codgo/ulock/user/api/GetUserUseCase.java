package com.codgo.ulock.user.api;

import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.api.UserView;
import java.util.Optional;

/** Read access to users for other slices. Every lookup is scoped to one tenant. */
public interface GetUserUseCase {

    /** @throws com.codgo.ulock.sharedkernel.exception.DomainException (NOT_FOUND) if the tenant has no such user */
    UserView getUser(TenantId tenantId, UserId userId);

    Optional<UserView> findUser(TenantId tenantId, UserId userId);

    /** Empty when no user matches, including when {@code email} is not a valid address. */
    Optional<UserView> findUserByEmail(TenantId tenantId, String email);

    boolean hasUsers(TenantId tenantId);
}
