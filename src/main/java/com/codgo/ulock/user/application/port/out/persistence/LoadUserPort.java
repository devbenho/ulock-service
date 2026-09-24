package com.codgo.ulock.user.application.port.out.persistence;

import com.codgo.ulock.sharedkernel.paging.PageQuery;
import com.codgo.ulock.sharedkernel.paging.PageResult;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.domain.model.User;
import java.util.Optional;

/** Every lookup is scoped to one tenant: a user is never visible through another tenant's id. */
public interface LoadUserPort {

    Optional<User> loadUser(TenantId tenantId, UserId userId);

    /** Locks the row for the rest of the transaction, so concurrent failed logins cannot under-count. */
    Optional<User> loadUserByEmailForUpdate(TenantId tenantId, Email email);

    Optional<User> loadUserByEmail(TenantId tenantId, Email email);

    boolean existsByEmail(TenantId tenantId, Email email);

    boolean existsInTenant(TenantId tenantId);

    PageResult<User> loadUsers(TenantId tenantId, UserFilter filter, PageQuery page);
}
