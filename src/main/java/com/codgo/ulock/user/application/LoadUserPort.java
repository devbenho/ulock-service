package com.codgo.ulock.user.application;

import com.codgo.ulock.sharedkernel.paging.PageQuery;
import com.codgo.ulock.sharedkernel.paging.PageResult;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.api.UserView;
import com.codgo.ulock.user.domain.User;
import com.codgo.ulock.user.domain.UserStatus;
import java.util.Optional;

/**
 * Every lookup requires the tenant: a user is never visible through another tenant's id.
 * Commands load the {@link User} aggregate. Queries read {@link UserView} projections and never
 * hydrate the aggregate.
 */
public interface LoadUserPort {

    // --- for commands -----------------------------------------------------------------------

    Optional<User> findById(TenantId tenantId, UserId userId);

    /** Locks the row for the rest of the transaction, so concurrent failed logins cannot under-count. */
    Optional<User> findByEmailForUpdate(TenantId tenantId, Email email);

    boolean existsByEmail(TenantId tenantId, Email email);

    // --- for queries ------------------------------------------------------------------------

    Optional<UserView> findViewById(TenantId tenantId, UserId userId);

    Optional<UserView> findViewByEmail(TenantId tenantId, Email email);

    /** @param status null for users of any status */
    PageResult<UserView> findViews(TenantId tenantId, UserStatus status, PageQuery page);

    boolean existsInTenant(TenantId tenantId);
}
