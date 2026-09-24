package com.codgo.ulock.user.adapter.out.persistence.repository;

import com.codgo.ulock.user.adapter.out.persistence.entity.UserJpaEntity;
import com.codgo.ulock.user.domain.model.UserStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Every finder takes the tenant id. The inherited id-only methods are for saving, never for lookups. */
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<UserJpaEntity> findByTenantIdAndEmail(UUID tenantId, String email);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);

    boolean existsByTenantId(UUID tenantId);

    Page<UserJpaEntity> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<UserJpaEntity> findAllByTenantIdAndStatus(UUID tenantId, UserStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserJpaEntity u where u.tenantId = :tenantId and u.email = :email")
    Optional<UserJpaEntity> findForUpdateByTenantIdAndEmail(UUID tenantId, String email);
}
