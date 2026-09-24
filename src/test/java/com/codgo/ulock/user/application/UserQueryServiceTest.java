package com.codgo.ulock.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codgo.ulock.sharedkernel.paging.PageQuery;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.application.LoadUserPort;
import com.codgo.ulock.user.domain.UserNotFoundException;
import com.codgo.ulock.user.domain.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserQueryServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());

    private final LoadUserPort loadUsers = mock(LoadUserPort.class);
    private final UserQueryService service = new UserQueryService(loadUsers);

    @Test
    void getUserThrowsNotFoundForUsersOutsideTheTenant() {
        when(loadUsers.findViewById(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUser(TENANT, UserId.newId())).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void findUserByEmailNormalisesAndTreatsMalformedInputAsNoMatch() {
        assertThat(service.findUserByEmail(TENANT, "not-an-email")).isEmpty();
        verifyNoInteractions(loadUsers);

        service.findUserByEmail(TENANT, "Jane@Example.TEST");
        verify(loadUsers).findViewByEmail(TENANT, Email.of("jane@example.test"));
    }

    @Test
    void listReadsProjectionsWithTheStatusFilter() {
        PageQuery page = new PageQuery(0, 20, List.of());

        service.list(TENANT, UserStatus.INACTIVE, page);

        verify(loadUsers).findViews(TENANT, UserStatus.INACTIVE, page);
    }
}
