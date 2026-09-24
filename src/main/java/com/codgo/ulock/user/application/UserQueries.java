package com.codgo.ulock.user.application;

import com.codgo.ulock.sharedkernel.paging.PageQuery;
import com.codgo.ulock.sharedkernel.paging.PageResult;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.api.UserView;
import com.codgo.ulock.user.domain.UserNotFoundException;
import com.codgo.ulock.user.domain.UserStatus;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read side. Projections come straight from storage; the aggregate is never hydrated. These are
 * one-line lookups, so they share a class rather than each getting a query record and a handler.
 */
@Service
@Transactional(readOnly = true)
public class UserQueries {

    private final UserRepository users;

    UserQueries(UserRepository users) {
        this.users = users;
    }

    public UserView getUser(TenantId tenantId, UserId userId) {
        return findUser(tenantId, userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    public Optional<UserView> findUser(TenantId tenantId, UserId userId) {
        return users.findViewById(tenantId, userId);
    }

    public Optional<UserView> findUserByEmail(TenantId tenantId, String email) {
        return Email.tryParse(email).flatMap(address -> users.findViewByEmail(tenantId, address));
    }

    public boolean hasUsers(TenantId tenantId) {
        return users.existsInTenant(tenantId);
    }

    /** @param status null for users of any status */
    public PageResult<UserView> list(TenantId tenantId, UserStatus status, PageQuery page) {
        return users.findViews(tenantId, status, page);
    }
}
