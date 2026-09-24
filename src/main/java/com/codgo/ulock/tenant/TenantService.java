package com.codgo.ulock.tenant;

import com.codgo.ulock.audit.AuditAction;
import com.codgo.ulock.audit.AuditService;
import com.codgo.ulock.audit.AuditTarget;
import com.codgo.ulock.common.PlatformTenant;
import com.codgo.ulock.common.error.ConflictException;
import com.codgo.ulock.common.error.NotFoundException;
import com.codgo.ulock.role.ReservedRole;
import com.codgo.ulock.role.RoleAssignmentService;
import com.codgo.ulock.role.TenantRoleProvisioner;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.tenant.TenantDtos.CreateTenantRequest;
import com.codgo.ulock.tenant.TenantDtos.UpdateTenantRequest;
import com.codgo.ulock.user.api.CreateUserUseCase;
import com.codgo.ulock.user.api.CreateUserCommand;
import com.codgo.ulock.user.api.UserView;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final TenantRepository tenants;
    private final TenantRoleProvisioner roleProvisioner;
    private final CreateUserUseCase createUser;
    private final RoleAssignmentService roleAssignments;
    private final AuditService audit;

    TenantService(TenantRepository tenants, TenantRoleProvisioner roleProvisioner, CreateUserUseCase createUser,
                  RoleAssignmentService roleAssignments, AuditService audit) {
        this.tenants = tenants;
        this.roleProvisioner = roleProvisioner;
        this.createUser = createUser;
        this.roleAssignments = roleAssignments;
        this.audit = audit;
    }

    /** Creates the tenant, its reserved roles, and its first TENANT_ADMIN in one transaction. */
    @Transactional
    public Tenant create(CreateTenantRequest request) {
        if (tenants.existsBySlug(request.slug())) {
            throw new ConflictException("A tenant with slug '" + request.slug() + "' already exists");
        }
        Tenant tenant = tenants.save(new Tenant(request.name().trim(), request.slug()));
        audit.record(tenant.getId(), AuditAction.TENANT_CREATED, AuditTarget.of(AuditTarget.TENANT, tenant.getId()),
                Map.of("name", tenant.getName(), "slug", tenant.getSlug()));
        roleProvisioner.provision(tenant.getId());
        var admin = request.admin();
        UserView adminUser = createUser.createUser(new CreateUserCommand(
                TenantId.of(tenant.getId()), admin.email(), admin.fullName(), admin.password()));
        roleAssignments.assignReserved(tenant.getId(), adminUser.id().value(), ReservedRole.TENANT_ADMIN);
        return tenant;
    }

    @Transactional(readOnly = true)
    public Page<Tenant> list(TenantStatus status, Pageable pageable) {
        return status == null ? tenants.findAll(pageable) : tenants.findAllByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public Tenant get(UUID tenantId) {
        return tenants.findById(tenantId).orElseThrow(() -> new NotFoundException("Tenant", tenantId));
    }

    @Transactional(readOnly = true)
    public Optional<Tenant> findBySlug(String slug) {
        return tenants.findBySlug(slug);
    }

    @Transactional
    public Tenant update(UUID tenantId, UpdateTenantRequest request) {
        Tenant tenant = get(tenantId);
        Map<String, Object> changes = new HashMap<>();
        if (request.name() != null && !request.name().trim().equals(tenant.getName())) {
            tenant.rename(request.name().trim());
            changes.put("name", tenant.getName());
        }
        if (request.status() != null && request.status() != tenant.getStatus()) {
            if (PlatformTenant.is(tenantId)) {
                throw new ConflictException("The platform tenant cannot be suspended");
            }
            tenant.changeStatus(request.status());
            changes.put("status", tenant.getStatus());
        }
        if (!changes.isEmpty()) {
            audit.record(tenantId, AuditAction.TENANT_UPDATED, AuditTarget.of(AuditTarget.TENANT, tenantId), changes);
        }
        return tenant;
    }
}
