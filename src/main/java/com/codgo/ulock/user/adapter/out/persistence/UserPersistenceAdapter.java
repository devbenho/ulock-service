package com.codgo.ulock.user.adapter.out.persistence;

import com.codgo.ulock.sharedkernel.paging.PageQuery;
import com.codgo.ulock.sharedkernel.paging.PageResult;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.adapter.out.persistence.entity.UserJpaEntity;
import com.codgo.ulock.user.adapter.out.persistence.mapper.UserPersistenceMapper;
import com.codgo.ulock.user.adapter.out.persistence.repository.UserJpaRepository;
import com.codgo.ulock.user.application.port.out.persistence.LoadUserPort;
import com.codgo.ulock.user.application.port.out.persistence.SaveUserPort;
import com.codgo.ulock.user.application.port.out.persistence.UserFilter;
import com.codgo.ulock.user.domain.model.User;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
class UserPersistenceAdapter implements LoadUserPort, SaveUserPort {

    private final UserJpaRepository repository;
    private final UserPersistenceMapper mapper;

    UserPersistenceAdapter(UserJpaRepository repository, UserPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<User> loadUser(TenantId tenantId, UserId userId) {
        return repository.findByIdAndTenantId(userId.value(), tenantId.value()).map(this::toDomain);
    }

    @Override
    public Optional<User> loadUserByEmailForUpdate(TenantId tenantId, Email email) {
        return repository.findForUpdateByTenantIdAndEmail(tenantId.value(), email.value()).map(this::toDomain);
    }

    @Override
    public Optional<User> loadUserByEmail(TenantId tenantId, Email email) {
        return repository.findByTenantIdAndEmail(tenantId.value(), email.value()).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(TenantId tenantId, Email email) {
        return repository.existsByTenantIdAndEmail(tenantId.value(), email.value());
    }

    @Override
    public boolean existsInTenant(TenantId tenantId) {
        return repository.existsByTenantId(tenantId.value());
    }

    @Override
    public PageResult<User> loadUsers(TenantId tenantId, UserFilter filter, PageQuery page) {
        Page<UserJpaEntity> rows = repository.findAll(matching(tenantId, filter), toPageRequest(page));
        return new PageResult<>(rows.getContent().stream().map(this::toDomain).toList(),
                rows.getNumber(), rows.getSize(), rows.getTotalElements());
    }

    /** Merges into the managed row when one is loaded (e.g. locked for login), inserts otherwise. */
    @Override
    public void save(User user) {
        repository.save(mapper.toEntity(user.toSnapshot()));
    }

    private User toDomain(UserJpaEntity entity) {
        return User.fromSnapshot(mapper.toSnapshot(entity));
    }

    private static Specification<UserJpaEntity> matching(TenantId tenantId, UserFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId.value()));
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.ids() != null) {
                predicates.add(root.get("id").in(filter.ids().stream().map(UserId::value).toList()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static PageRequest toPageRequest(PageQuery page) {
        Sort sort = Sort.by(page.sort().stream()
                .map(s -> new Sort.Order(s.direction() == PageQuery.Direction.ASC ? Sort.Direction.ASC : Sort.Direction.DESC,
                        s.property()))
                .toList());
        return PageRequest.of(page.page(), page.size(), sort);
    }
}
