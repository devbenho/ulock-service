package com.codgo.ulock.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);

    boolean existsByTenantId(UUID tenantId);

    Page<User> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<User> findAllByTenantIdAndStatus(UUID tenantId, UserStatus status, Pageable pageable);

    /** Row-locks the user so concurrent failed logins cannot under-count towards the lockout. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.tenantId = :tenantId and u.email = :email")
    Optional<User> findForLogin(UUID tenantId, String email);
}
