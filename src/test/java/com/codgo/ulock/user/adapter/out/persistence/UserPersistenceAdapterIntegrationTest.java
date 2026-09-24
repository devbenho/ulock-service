package com.codgo.ulock.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codgo.ulock.sharedkernel.paging.PageQuery.Direction;
import com.codgo.ulock.sharedkernel.paging.PageQuery;
import com.codgo.ulock.sharedkernel.paging.PageResult;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.support.PostgresTestcontainer;
import com.codgo.ulock.user.adapter.out.persistence.mapper.UserPersistenceMapperImpl;
import com.codgo.ulock.user.adapter.out.persistence.repository.UserJpaRepository;
import com.codgo.ulock.user.application.port.out.persistence.UserFilter;
import com.codgo.ulock.user.domain.model.User;
import com.codgo.ulock.user.domain.model.UserStatus;
import com.codgo.ulock.user.domain.policy.LockoutPolicy;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
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
@Import({PostgresTestcontainer.class, UserPersistenceAdapter.class, UserPersistenceMapperImpl.class})
class UserPersistenceAdapterIntegrationTest {

    /** Postgres keeps microseconds; truncating keeps round-trip comparisons exact. */
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final PageQuery FIRST_PAGE = new PageQuery(0, 20, List.of(new PageQuery.Sort("email", Direction.ASC)));

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
    void savesAndRehydratesEveryField() {
        User user = user(tenantA, "round@trip.test");
        user.recordLoginAttempt(false, new LockoutPolicy(5, Duration.ofMinutes(15), Duration.ofMinutes(15)), NOW);
        adapter.save(user);

        User loaded = adapter.loadUser(tenantA, user.id()).orElseThrow();

        assertThat(loaded.toSnapshot()).isEqualTo(user.toSnapshot());
    }

    @Test
    void savingAnExistingUserUpdatesIt() {
        User user = user(tenantA, "update@me.test");
        adapter.save(user);

        User loaded = adapter.loadUser(tenantA, user.id()).orElseThrow();
        loaded.rename("Renamed", NOW.plusSeconds(1));
        loaded.deactivate(null, NOW.plusSeconds(1));
        adapter.save(loaded);

        User reloaded = adapter.loadUser(tenantA, user.id()).orElseThrow();
        assertThat(reloaded.fullName()).isEqualTo("Renamed");
        assertThat(reloaded.status()).isEqualTo(UserStatus.INACTIVE);
        assertThat(reloaded.createdAt()).isEqualTo(NOW);
    }

    @Test
    void aUserOfTenantACannotBeReadThroughTenantB() {
        User user = user(tenantA, "isolated@a.test");
        adapter.save(user);

        assertThat(adapter.loadUser(tenantB, user.id())).isEmpty();
        assertThat(adapter.loadUserByEmail(tenantB, user.email())).isEmpty();
        assertThat(adapter.loadUserByEmailForUpdate(tenantB, user.email())).isEmpty();
        assertThat(adapter.existsByEmail(tenantB, user.email())).isFalse();
        assertThat(adapter.existsInTenant(tenantB)).isFalse();
        assertThat(adapter.loadUsers(tenantB, UserFilter.none(), FIRST_PAGE).content()).isEmpty();
        assertThat(adapter.loadUsers(tenantB, UserFilter.byIds(Set.of(user.id())), FIRST_PAGE).content()).isEmpty();

        assertThat(adapter.loadUser(tenantA, user.id())).isPresent();
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
    void listsWithStatusAndIdFiltersSortingAndPaging() {
        User anna = user(tenantA, "anna@example.test");
        User bob = user(tenantA, "bob@example.test");
        User carl = user(tenantA, "carl@example.test");
        carl.deactivate(null, NOW);
        List.of(anna, bob, carl).forEach(adapter::save);

        PageResult<User> firstTwo = adapter.loadUsers(tenantA, UserFilter.none(), new PageQuery(0, 2,
                List.of(new PageQuery.Sort("email", Direction.DESC))));
        assertThat(firstTwo.content()).extracting(User::email).containsExactly(carl.email(), bob.email());
        assertThat(firstTwo.totalElements()).isEqualTo(3);
        assertThat(firstTwo.totalPages()).isEqualTo(2);

        assertThat(adapter.loadUsers(tenantA, UserFilter.byStatus(UserStatus.INACTIVE), FIRST_PAGE).content())
                .extracting(User::id).containsExactly(carl.id());
        assertThat(adapter.loadUsers(tenantA, UserFilter.byIds(Set.of(anna.id(), carl.id())), FIRST_PAGE).content())
                .extracting(User::id).containsExactly(anna.id(), carl.id());
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
