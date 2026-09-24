package com.codgo.ulock.user.adapter.out.persistence;

import com.codgo.ulock.sharedkernel.paging.PageQuery;
import com.codgo.ulock.sharedkernel.paging.PageResult;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.adapter.out.persistence.entity.UserJpaEntity;
import com.codgo.ulock.user.adapter.out.persistence.repository.UserJpaRepository;
import com.codgo.ulock.user.application.port.in.model.UserView;
import com.codgo.ulock.user.application.port.out.persistence.LoadUserPort;
import com.codgo.ulock.user.application.port.out.persistence.SaveUserPort;
import com.codgo.ulock.user.domain.model.User;
import com.codgo.ulock.user.domain.model.UserStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** Maps between the {@code users} row and the domain by hand: one entity, one aggregate, no in-between model. */
@Component
class UserPersistenceAdapter implements LoadUserPort, SaveUserPort {

    private final UserJpaRepository repository;

    UserPersistenceAdapter(UserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<User> findById(TenantId tenantId, UserId userId) {
        return repository.findByTenantIdAndId(tenantId.value(), userId.value()).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<User> findByEmailForUpdate(TenantId tenantId, Email email) {
        return repository.findForUpdateByTenantIdAndEmail(tenantId.value(), email.value())
                .map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public boolean existsByEmail(TenantId tenantId, Email email) {
        return repository.existsByTenantIdAndEmail(tenantId.value(), email.value());
    }

    @Override
    public Optional<UserView> findViewById(TenantId tenantId, UserId userId) {
        return repository.findByTenantIdAndId(tenantId.value(), userId.value()).map(UserPersistenceAdapter::toView);
    }

    @Override
    public Optional<UserView> findViewByEmail(TenantId tenantId, Email email) {
        return repository.findByTenantIdAndEmail(tenantId.value(), email.value()).map(UserPersistenceAdapter::toView);
    }

    @Override
    public PageResult<UserView> findViews(TenantId tenantId, UserStatus status, PageQuery page) {
        PageRequest request = toPageRequest(page);
        Page<UserJpaEntity> rows = status == null
                ? repository.findAllByTenantId(tenantId.value(), request)
                : repository.findAllByTenantIdAndStatus(tenantId.value(), status, request);
        return new PageResult<>(rows.getContent().stream().map(UserPersistenceAdapter::toView).toList(),
                rows.getNumber(), rows.getSize(), rows.getTotalElements());
    }

    @Override
    public boolean existsInTenant(TenantId tenantId) {
        return repository.existsByTenantId(tenantId.value());
    }

    /** Merges into the managed row when one is loaded (e.g. locked for login), inserts otherwise. */
    @Override
    public void save(User user) {
        repository.save(toEntity(user));
    }

    private static User toDomain(UserJpaEntity e) {
        return User.restore(UserId.of(e.getId()), TenantId.of(e.getTenantId()), Email.of(e.getEmail()),
                e.getPasswordHash(), e.getFullName(), e.getStatus(), e.getLastLoginAt(), e.getFailedLoginCount(),
                e.getFailedLoginWindowStart(), e.getLockedUntil(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private static UserView toView(UserJpaEntity e) {
        return new UserView(UserId.of(e.getId()), TenantId.of(e.getTenantId()), Email.of(e.getEmail()),
                e.getFullName(), e.getStatus().name(), e.getStatus() == UserStatus.ACTIVE, e.getLockedUntil(),
                e.getLastLoginAt(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private static UserJpaEntity toEntity(User user) {
        UserJpaEntity e = new UserJpaEntity();
        e.setId(user.id().value());
        e.setTenantId(user.tenantId().value());
        e.setEmail(user.email().value());
        e.setPasswordHash(user.passwordHash());
        e.setFullName(user.fullName());
        e.setStatus(user.status());
        e.setLastLoginAt(user.lastLoginAt());
        e.setFailedLoginCount(user.failedLoginCount());
        e.setFailedLoginWindowStart(user.failedLoginWindowStart());
        e.setLockedUntil(user.lockedUntil());
        e.setCreatedAt(user.createdAt());
        e.setUpdatedAt(user.updatedAt());
        return e;
    }

    private static PageRequest toPageRequest(PageQuery page) {
        Sort sort = Sort.by(page.sort().stream()
                .map(s -> new Sort.Order(s.direction() == PageQuery.Direction.ASC ? Sort.Direction.ASC : Sort.Direction.DESC,
                        s.property()))
                .toList());
        return PageRequest.of(page.page(), page.size(), sort);
    }
}
