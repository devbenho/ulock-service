package com.codgo.ulock.role;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    boolean existsByIdRoleId(UUID roleId);

    /** Atomic, race-free assignment: returns 0 when the user already holds the role. */
    @Modifying
    @Query(value = """
            insert into user_roles (user_id, role_id, assigned_by, assigned_at)
            values (:userId, :roleId, :assignedBy, :assignedAt)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(UUID userId, UUID roleId, UUID assignedBy, Instant assignedAt);

    @Query("select r from Role r where r.id in (select ur.id.roleId from UserRole ur where ur.id.userId = :userId)"
            + " order by r.name")
    List<Role> findRolesOfUser(UUID userId);

    @Query("select ur.id.userId from UserRole ur where ur.id.roleId = :roleId")
    List<UUID> findMemberIds(UUID roleId);
}
