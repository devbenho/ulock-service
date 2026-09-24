package com.codgo.ulock.role;

import com.codgo.ulock.common.PlatformTenant;
import com.codgo.ulock.common.error.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "roles")
public class Role {

    public static final int MAX_NAME_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    private String description;

    @ManyToMany
    @JoinTable(name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected Role() {}

    Role(UUID tenantId, String name, String description) {
        this.tenantId = tenantId;
        this.name = normalizeName(name);
        this.description = description;
    }

    /**
     * Role names are trimmed and runs of whitespace collapse to one space. The case the admin typed is
     * kept for display. Uniqueness within a tenant ignores case (enforced by {@code uq_roles_tenant_name}).
     */
    public static String normalizeName(String name) {
        String normalized = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty() || normalized.length() > MAX_NAME_LENGTH) {
            throw new InvalidRequestException("Role name must be 1 to " + MAX_NAME_LENGTH + " characters");
        }
        return normalized;
    }

    public boolean isReserved() {
        return ReservedRole.isReserved(name);
    }

    void rename(String name) {
        this.name = normalizeName(name);
    }

    void describe(String description) {
        this.description = description;
    }

    void replacePermissions(Collection<Permission> newPermissions) {
        requireCanHold(newPermissions);
        permissions.clear();
        permissions.addAll(newPermissions);
    }

    /**
     * A role may hold its own tenant's permissions and system permissions. Platform-only system
     * permissions are allowed only in the platform tenant.
     */
    void requireCanHold(Collection<Permission> candidates) {
        for (Permission permission : candidates) {
            if (!permission.isSystem() && !permission.getTenantId().equals(tenantId)) {
                throw new InvalidRequestException("Permission " + permission.getCode() + " belongs to another tenant");
            }
            if (permission.isPlatformOnly() && !PlatformTenant.is(tenantId)) {
                throw new InvalidRequestException("Permission " + permission.getCode() + " is platform-only");
            }
        }
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Set<Permission> getPermissions() { return Set.copyOf(permissions); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
