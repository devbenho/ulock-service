package com.codgo.ulock.user.application.service;

import com.codgo.ulock.sharedkernel.paging.PageQuery;
import com.codgo.ulock.sharedkernel.paging.PageResult;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.application.port.in.GetUserUseCase;
import com.codgo.ulock.user.application.port.in.model.UserView;
import com.codgo.ulock.user.application.port.out.persistence.LoadUserPort;
import com.codgo.ulock.user.application.port.out.persistence.UserFilter;
import com.codgo.ulock.user.application.service.mapper.UserViews;
import com.codgo.ulock.user.domain.exception.UserNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetUserService implements GetUserUseCase {

    private final LoadUserPort loadUsers;

    public GetUserService(LoadUserPort loadUsers) {
        this.loadUsers = loadUsers;
    }

    @Override
    public UserView getUser(TenantId tenantId, UserId userId) {
        return findUser(tenantId, userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Override
    public Optional<UserView> findUser(TenantId tenantId, UserId userId) {
        return loadUsers.loadUser(tenantId, userId).map(UserViews::from);
    }

    @Override
    public Optional<UserView> findUserByEmail(TenantId tenantId, String email) {
        return Email.tryParse(email).flatMap(address -> loadUsers.loadUserByEmail(tenantId, address))
                .map(UserViews::from);
    }

    @Override
    public PageResult<UserView> listUsers(TenantId tenantId, Collection<UserId> userIds, PageQuery page) {
        if (userIds.isEmpty()) {
            return new PageResult<>(List.of(), page.page(), page.size(), 0);
        }
        return loadUsers.loadUsers(tenantId, UserFilter.byIds(Set.copyOf(userIds)), page).map(UserViews::from);
    }

    @Override
    public boolean hasUsers(TenantId tenantId) {
        return loadUsers.existsInTenant(tenantId);
    }
}
