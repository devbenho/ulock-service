package com.codgo.ulock.role;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Assignment of a role to a user; both belong to the same tenant. Rows are inserted atomically by
 * {@link UserRoleRepository#insertIfAbsent}.
 */
@Entity
@Table(name = "user_roles")
class UserRole {

    @EmbeddedId
    private UserRoleId id;

    private UUID assignedBy;

    @Column(nullable = false, updatable = false)
    private Instant assignedAt;

    protected UserRole() {}
}
