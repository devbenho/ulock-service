package com.codgo.ulock.user.application.port.in;

import com.codgo.ulock.sharedkernel.paging.PageQuery;
import com.codgo.ulock.sharedkernel.paging.PageResult;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.application.port.in.model.UserView;
import java.util.Collection;
import java.util.Optional;

/** Read access to users for other slices. Every lookup is scoped to one tenant. */
public interface GetUserUseCase {

    /** @throws com.codgo.ulock.sharedkernel.exception.DomainException (NOT_FOUND) if the tenant has no such user */
    UserView getUser(TenantId tenantId, UserId userId);

    Optional<UserView> findUser(TenantId tenantId, UserId userId);

    /** Empty when no user matches, including when {@code email} is not a valid address. */
    Optional<UserView> findUserByEmail(TenantId tenantId, String email);

    /** The given users of the tenant; ids from other tenants are silently excluded. */
    PageResult<UserView> listUsers(TenantId tenantId, Collection<UserId> userIds, PageQuery page);

    boolean hasUsers(TenantId tenantId);
}
