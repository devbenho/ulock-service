package com.codgo.ulock.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codgo.ulock.sharedkernel.paging.PageQuery;
import com.codgo.ulock.sharedkernel.paging.PageQuery.Direction;
import com.codgo.ulock.sharedkernel.paging.PageResult;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.support.PostgresTestcontainer;
import com.codgo.ulock.user.adapter.out.persistence.repository.UserJpaRepository;
import com.codgo.ulock.user.application.port.in.model.UserView;
import com.codgo.ulock.user.domain.model.User;
import com.codgo.ulock.user.domain.model.UserStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Runs the adapter against a real PostgreSQL with the Flyway schema. Each test rolls back. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainer.class, UserPersistenceAdapter.class})
class UserPersistenceAdapterIntegrationTest {

    /** Postgres keeps microseconds; truncating keeps round-trip comparisons exact. */
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final PageQuery BY_EMAIL = new PageQuery(0, 20, List.of(new PageQuery.Sort("email", Direction.ASC)));

    @Autowired
    UserPersistenceAdapter adapter;

    @Autowired
    UserJpaRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    TenantId tenantA;
    TenantId tenantB;

    @BeforeEach
    void createTenants() {
        tenantA = insertTenant();
        tenantB = insertTenant();
    }

    @Test
    void savesAndRestoresEveryField() {
        User user = user(tenantA, "round@trip.test");
        user.recordLoginAttempt(false, NOW);
        adapter.save(user);

        User loaded = adapter.findById(tenantA, user.id()).orElseThrow();

        assertThat(loaded).usingRecursiveComparison().ignoringFields("domainEvents").isEqualTo(user);
        assertThat(loaded.failedLoginCount()).isEqualTo(1);
    }

    @Test
    void savingAnExistingUserUpdatesIt() {
        User user = user(tenantA, "update@me.test");
        adapter.save(user);

        User loaded = adapter.findById(tenantA, user.id()).orElseThrow();
        loaded.rename("Renamed", NOW.plusSeconds(1));
        loaded.deactivate(null, NOW.plusSeconds(1));
        adapter.save(loaded);

        UserView view = adapter.findViewById(tenantA, user.id()).orElseThrow();
        assertThat(view.fullName()).isEqualTo("Renamed");
        assertThat(view.status()).isEqualTo("INACTIVE");
        assertThat(view.active()).isFalse();
        assertThat(view.createdAt()).isEqualTo(NOW);
        assertThat(view.updatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void aUserOfTenantACannotBeReadThroughTenantB() {
        User user = user(tenantA, "isolated@a.test");
        adapter.save(user);

        assertThat(adapter.findById(tenantB, user.id())).isEmpty();
        assertThat(adapter.findByEmailForUpdate(tenantB, user.email())).isEmpty();
        assertThat(adapter.existsByEmail(tenantB, user.email())).isFalse();
        assertThat(adapter.findViewById(tenantB, user.id())).isEmpty();
        assertThat(adapter.findViewByEmail(tenantB, user.email())).isEmpty();
        assertThat(adapter.findViews(tenantB, null, BY_EMAIL).content()).isEmpty();
        assertThat(adapter.existsInTenant(tenantB)).isFalse();

        assertThat(adapter.findById(tenantA, user.id())).isPresent();
        assertThat(adapter.findViewByEmail(tenantA, user.email())).isPresent();
    }

    @Test
    void theSameEmailMayExistOnceInEachTenant() {
        adapter.save(user(tenantA, "shared@example.test"));
        adapter.save(user(tenantB, "shared@example.test"));
        repository.flush();

        assertThat(adapter.existsByEmail(tenantA, Email.of("shared@example.test"))).isTrue();
        adapter.save(user(tenantA, "shared@example.test"));
        assertThatThrownBy(repository::flush).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listsProjectionsWithStatusFilterSortingAndPaging() {
        User anna = user(tenantA, "anna@example.test");
        User bob = user(tenantA, "bob@example.test");
        User carl = user(tenantA, "carl@example.test");
        carl.deactivate(null, NOW);
        List.of(anna, bob, carl).forEach(adapter::save);

        PageResult<UserView> firstTwo = adapter.findViews(tenantA, null,
                new PageQuery(0, 2, List.of(new PageQuery.Sort("email", Direction.DESC))));
        assertThat(firstTwo.content()).extracting(UserView::email).containsExactly(carl.email(), bob.email());
        assertThat(firstTwo.totalElements()).isEqualTo(3);
        assertThat(firstTwo.totalPages()).isEqualTo(2);

        assertThat(adapter.findViews(tenantA, UserStatus.INACTIVE, BY_EMAIL).content())
                .extracting(UserView::id).containsExactly(carl.id());
    }

    private User user(TenantId tenant, String email) {
        User user = User.register(tenant, Email.of(email), "hash", "User " + email, NOW);
        user.pullDomainEvents();
        return user;
    }

    private TenantId insertTenant() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into tenants (id, name, slug, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', now(), now())",
                id, "Tenant " + id, "t-" + id);
        return TenantId.of(id);
    }
}
