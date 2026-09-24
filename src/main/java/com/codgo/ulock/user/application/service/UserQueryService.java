package com.codgo.ulock.user.application.service;

import com.codgo.ulock.sharedkernel.paging.PageQuery;
import com.codgo.ulock.sharedkernel.paging.PageResult;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.application.port.in.GetUserUseCase;
import com.codgo.ulock.user.application.port.in.model.UserView;
import com.codgo.ulock.user.application.port.out.persistence.LoadUserPort;
import com.codgo.ulock.user.domain.exception.UserNotFoundException;
import com.codgo.ulock.user.domain.model.UserStatus;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only user queries: projections straight from storage, never the aggregate. */
@Service
@Transactional(readOnly = true)
public class UserQueryService implements GetUserUseCase {

    private final LoadUserPort loadUsers;

    public UserQueryService(LoadUserPort loadUsers) {
        this.loadUsers = loadUsers;
    }

    @Override
    public UserView getUser(TenantId tenantId, UserId userId) {
        return findUser(tenantId, userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Override
    public Optional<UserView> findUser(TenantId tenantId, UserId userId) {
        return loadUsers.findViewById(tenantId, userId);
    }

    @Override
    public Optional<UserView> findUserByEmail(TenantId tenantId, String email) {
        return Email.tryParse(email).flatMap(address -> loadUsers.findViewByEmail(tenantId, address));
    }

    @Override
    public boolean hasUsers(TenantId tenantId) {
        return loadUsers.existsInTenant(tenantId);
    }

    /** @param status null for users of any status */
    public PageResult<UserView> list(TenantId tenantId, UserStatus status, PageQuery page) {
        return loadUsers.findViews(tenantId, status, page);
    }
}
