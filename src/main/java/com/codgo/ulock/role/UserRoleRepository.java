package com.codgo.ulock.role;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Writes to {@code user_roles}. Every operation takes the tenant and matches the user and the role
 * against it in the same statement, so an assignment can never cross tenants. It deliberately
 * extends {@link Repository}, not JpaRepository, so no id-only operations exist.
 */
interface UserRoleRepository extends Repository<UserRole, UserRoleId> {

    /** Assigns atomically and race-free. Returns 0 if already assigned, or if the user or role is not in the tenant. */
    @Modifying
    @Query(value = """
            insert into user_roles (user_id, role_id, assigned_by, assigned_at)
            select u.id, r.id, :assignedBy, :assignedAt
            from users u
            join roles r on r.tenant_id = u.tenant_id
            where u.tenant_id = :tenantId and u.id = :userId and r.id = :roleId
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(UUID tenantId, UUID userId, UUID roleId, UUID assignedBy, Instant assignedAt);

    /** @return the number of assignments removed: 0 or 1 */
    @Modifying
    @Query(value = """
            delete from user_roles ur
            using roles r
            where r.id = ur.role_id and r.tenant_id = :tenantId and ur.user_id = :userId and ur.role_id = :roleId
            """, nativeQuery = true)
    int delete(UUID tenantId, UUID userId, UUID roleId);

    @Query("""
            select count(ur) > 0 from UserRole ur, Role r
            where r.id = ur.id.roleId and r.tenantId = :tenantId and r.id = :roleId
            """)
    boolean hasMembers(UUID tenantId, UUID roleId);
}
