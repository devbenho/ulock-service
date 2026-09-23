package com.codgo.ulock.role;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PermissionRepository extends JpaRepository<Permission, UUID> {

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByTenantIdIsNullAndCode(String code);

    List<Permission> findAllByTenantIdIsNullAndCodeIn(Collection<String> codes);

    /** The tenant's own permissions plus every system permission it is allowed to use. */
    @Query("""
            select p from Permission p
            where p.tenantId = :tenantId
               or (p.tenantId is null and p.code not in :excludedSystemCodes)
            """)
    Page<Permission> findUsableBy(UUID tenantId, Collection<String> excludedSystemCodes, Pageable pageable);

    @Query("select p from Permission p where p.id in :ids and (p.tenantId = :tenantId or p.tenantId is null)")
    List<Permission> findAllByIdVisibleTo(Collection<UUID> ids, UUID tenantId);
}
