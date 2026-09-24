package com.codgo.ulock.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codgo.ulock.audit.AuditService;
import com.codgo.ulock.common.error.ConflictException;
import com.codgo.ulock.common.error.InvalidRequestException;
import com.codgo.ulock.role.RoleDtos.CreatePermissionRequest;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PermissionServiceTest {

    private final PermissionRepository repository = mock(PermissionRepository.class);
    private final PermissionService service = new PermissionService(repository, mock(AuditService.class));
    private final UUID tenantId = UUID.randomUUID();

    @Test
    void idsNotVisibleToTheTenantAreReportedAsUnknown() throws Exception {
        Permission userRead = permission(null, "user:read");
        UUID foreign = UUID.randomUUID();
        when(repository.findAllByIdVisibleTo(any(), any())).thenReturn(List.of(userRead));

        assertThatThrownBy(() -> service.resolveVisible(tenantId, Set.of(userRead.getId(), foreign)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unknown permission ids: " + foreign + "'");
    }

    @Test
    void codesThatShadowASystemPermissionConflict() {
        when(repository.existsByTenantIdIsNullAndCode("user:read")).thenReturn(true);

        assertThatThrownBy(() -> service.create(tenantId, new CreatePermissionRequest("user:read", null)))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void reservedRoleNamesMatchCaseInsensitively() {
        assertThat(ReservedRole.isReserved("tenant_admin")).isTrue();
        assertThat(ReservedRole.isReserved("Editor")).isFalse();
    }

    private static Permission permission(UUID tenantId, String code) throws Exception {
        Permission permission = new Permission(tenantId, code, null);
        Field id = Permission.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(permission, UUID.randomUUID());
        return permission;
    }
}
