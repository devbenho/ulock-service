package com.codgo.ulock.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codgo.ulock.sharedkernel.paging.PageQuery;
import com.codgo.ulock.sharedkernel.paging.PageResult;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.application.port.out.persistence.LoadUserPort;
import com.codgo.ulock.user.application.port.out.persistence.UserFilter;
import com.codgo.ulock.user.domain.exception.UserNotFoundException;
import com.codgo.ulock.user.domain.model.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetUserServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final PageQuery PAGE = new PageQuery(0, 20, List.of());

    private final LoadUserPort loadUsers = mock(LoadUserPort.class);
    private final GetUserService service = new GetUserService(loadUsers);

    @Test
    void mapsUsersToViewsWithoutExposingTheDomain() {
        User user = User.register(TENANT, Email.of("v@example.test"), "h", "Viewed", Instant.now());
        when(loadUsers.loadUser(TENANT, user.id())).thenReturn(Optional.of(user));

        var view = service.getUser(TENANT, user.id());

        assertThat(view.email()).isEqualTo(user.email());
        assertThat(view.fullName()).isEqualTo("Viewed");
        assertThat(view.status()).isEqualTo("ACTIVE");
    }

    @Test
    void getUserThrowsNotFound() {
        when(loadUsers.loadUser(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUser(TENANT, UserId.newId())).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void findUserByEmailTreatsMalformedInputAsNoMatch() {
        assertThat(service.findUserByEmail(TENANT, "not-an-email")).isEmpty();
        verifyNoInteractions(loadUsers);
    }

    @Test
    void listingNoIdsSkipsTheQuery() {
        assertThat(service.listUsers(TENANT, List.of(), PAGE).content()).isEmpty();
        verifyNoInteractions(loadUsers);
    }

    @Test
    void listingByIdsFiltersInTheTenant() {
        UserId id = UserId.newId();
        when(loadUsers.loadUsers(any(), any(), any())).thenReturn(new PageResult<>(List.of(), 0, 20, 0));

        service.listUsers(TENANT, List.of(id), PAGE);

        verify(loadUsers).loadUsers(TENANT, UserFilter.byIds(Set.of(id)), PAGE);
    }
}
