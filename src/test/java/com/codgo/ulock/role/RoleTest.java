package com.codgo.ulock.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codgo.ulock.common.PlatformTenant;
import com.codgo.ulock.common.error.InvalidRequestException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoleTest {

    private final UUID tenantId = UUID.randomUUID();
    private final Role role = new Role(tenantId, "Editor", null);

    @Test
    void namesAreTrimmedWithInternalWhitespaceCollapsedAndCaseKept() {
        assertThat(new Role(tenantId, "  Invoice \t  Approvers ", null).getName()).isEqualTo("Invoice Approvers");
        assertThatThrownBy(() -> Role.normalizeName("   ")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> Role.normalizeName("x".repeat(Role.MAX_NAME_LENGTH + 1)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void mayHoldItsOwnTenantsAndSystemPermissions() {
        role.replacePermissions(List.of(new Permission(tenantId, "invoice:approve", null),
                new Permission(null, "user:read", null)));

        assertThat(role.getPermissions()).extracting(Permission::getCode)
                .containsExactlyInAnyOrder("invoice:approve", "user:read");
    }

    @Test
    void mayNotHoldAnotherTenantsPermission() {
        Permission foreign = new Permission(UUID.randomUUID(), "invoice:approve", null);

        assertThatThrownBy(() -> role.replacePermissions(List.of(foreign)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("another tenant");
        assertThat(role.getPermissions()).isEmpty();
    }

    @Test
    void platformOnlyPermissionsBelongToThePlatformTenantAlone() {
        Permission tenantWrite = new Permission(null, "tenant:write", null);

        assertThatThrownBy(() -> role.replacePermissions(List.of(tenantWrite)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("platform-only");
        new Role(PlatformTenant.ID, "Operators", null).replacePermissions(List.of(tenantWrite));
    }

    @Test
    void permissionCodesMustBeCanonical() {
        assertThatThrownBy(() -> new Permission(tenantId, "Invoice:Approve", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Permission(tenantId, " invoice:approve", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
