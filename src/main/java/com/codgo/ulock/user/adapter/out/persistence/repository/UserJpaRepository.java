package com.codgo.ulock.user.adapter.out.persistence.repository;

import com.codgo.ulock.user.adapter.out.persistence.entity.UserJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID>, JpaSpecificationExecutor<UserJpaEntity> {

    Optional<UserJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<UserJpaEntity> findByTenantIdAndEmail(UUID tenantId, String email);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);

    boolean existsByTenantId(UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserJpaEntity u where u.tenantId = :tenantId and u.email = :email")
    Optional<UserJpaEntity> findForUpdateByTenantIdAndEmail(UUID tenantId, String email);
}
