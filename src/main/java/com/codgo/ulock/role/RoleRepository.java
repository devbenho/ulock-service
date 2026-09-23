package com.codgo.ulock.role;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Role> findByTenantIdAndName(UUID tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    Page<Role> findAllByTenantId(UUID tenantId, Pageable pageable);
}
