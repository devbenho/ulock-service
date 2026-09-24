# ulock-service

Multi-tenant authentication and RBAC service (Spring Boot 3.5, Java 21, PostgreSQL 16, Flyway).
Requirements live in `uLock Service — Engineering Requirements Document.md`, which is the source of truth
for behaviour; this file is the source of truth for code structure.

## Build and test

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # the machine default JDK may be newer
./mvnw verify                                   # unit + Testcontainers tests, ArchUnit, JaCoCo ≥ 80% gate
```

Docker must be running. If an IDE (e.g. VS Code's Java extension) is building the project at the same
time, it can overwrite `target/classes` with its own output. If a failure makes no sense, stop the IDE
build or run Maven from a copy of the tree.

## Target architecture

The codebase uses vertical slices, with DDD and hexagonal ideas applied only where they pay for
themselves. There is also a small shared kernel. The base package is `com.codgo.ulock`.

**Guiding rule.** Don't add a factory, policy, specification, domain service, port, adapter, value
object or domain event unless it solves a problem you have now.

**How much structure each slice gets:**

| Slice | Style |
|---|---|
| `user` | Rich domain model, structured as a hexagon (layout below) |
| `role` | Moderate: JPA entities with their invariants (`Role`, `Permission`), focused services, and a SQL read side (`RoleQueryRepository`) |
| `tenant` | Simple CRUD |
| `auth` | Application orchestration plus security adapters |
| `audit` | A simple immutable, append-only model |
| authorization and access summaries | Optimized read paths |

Keep the flat slices flat until they grow rules that need more.

Within every layer, related classes are grouped into their own subpackage by concept, as shown below.
Never leave events, exceptions, commands or DTOs loose next to the classes that use them.

```
com.codgo.ulock
├── sharedkernel/                 pure Java, immutable
│   ├── valueobject/              TenantId, UserId, RoleId, Email
│   ├── event/                    DomainEvent, DomainEventPublisherPort (one port for all slices)
│   ├── exception/                DomainException (+ Category → HTTP status)
│   └── paging/                   PageQuery, PageResult
├── <slice>/                      e.g. user (done), role, tenant, auth, audit (not yet migrated)
│   ├── domain/                   pure Java
│   │   ├── model/                aggregate root, enums, small result types
│   │   ├── policy/               stateless rules that are not an entity (PasswordPolicy)
│   │   ├── event/                domain events (records implementing DomainEvent)
│   │   └── exception/            DomainException subclasses
│   ├── application/
│   │   ├── port/in/              use-case interfaces called by OTHER slices only
│   │   │   ├── command/          input records of those use cases
│   │   │   ├── model/            read models returned to other slices (e.g. UserView)
│   │   │   └── event/            published events other slices may listen to
│   │   ├── port/out/<concern>/   outbound ports grouped by concern: persistence/, security/, …
│   │   └── service/              use-case implementations (+ command/, mapper/ subpackages)
│   └── adapter/
│       ├── in/web/               controller (+ dto/ for request/response records)
│       └── out/<concern>/        persistence/ (adapter + entity/, repository/, mapper/), security/, audit/, …
└── common/                       cross-cutting infrastructure: security, error handling, web helpers,
                                  event/SpringDomainEventPublisher
```

## Rules (non-negotiable)

1. **Dependency rule.** Dependencies go adapter → application → domain, never the reverse.
2. **Pure domain.** `domain/` and `sharedkernel/` are pure Java. They may not use Spring, JPA, Hibernate,
   Jackson, Lombok or validation annotations.
3. **Application layer.** `application/` depends only on its own domain and on the shared kernel. The only
   framework annotations allowed are `@Service` and `@Transactional`. It may also use `java.time.Clock`.
4. **Outbound ports.** Every dependency on infrastructure goes through an outbound port interface in
   `application/port/out`. That includes persistence, hashing, events, other slices, configuration and the
   current actor. Services never use repositories, BCrypt, Spring events or `SecurityContext` directly.
   The controller passes the actor's id in the command.
5. **Inbound ports.** Only use cases called from outside the slice get an inbound port. Controllers inside
   the slice call the services directly.
6. **JPA entities.** JPA entities (`*JpaEntity`) live only in `adapter/out/persistence/..`. They are never
   returned from controllers and never passed into the domain. The persistence adapter maps entity to
   aggregate by hand, with `User.restore(...)`, and the reverse through getters. There's no intermediate
   model and no mapping library.
7. **Business rules in the domain.** Examples: `User.deactivate()`, and the rule that no one may
   deactivate themselves. A deactivated user can only be reactivated through `activate()`.
   Cross-aggregate checks, such as email being unique per tenant, are done in the service through a port.
8. **Slice boundaries.** Other slices, and `common`, may only use `<slice>/application/port/in/..`. They may
   never use a slice's domain, adapter, application service or outbound ports. To react to another slice,
   listen to the events in its `port/in/event`. Its domain events are internal.
9. **Controllers.** Controllers return DTO records. Errors go through `common/error/GlobalExceptionHandler`
   as RFC 7807 ProblemDetail. A `DomainException`'s category decides the HTTP status.

`src/test/java/com/codgo/ulock/ArchitectureTest.java` enforces rules 1–3, 6 and 8 for every slice,
discovered automatically. Keep it green. Never weaken a rule to make a change pass.

## Commands, queries and authorization

- **Commands** load the aggregate through a tenant-scoped port, call its methods, save it and publish
  its events.
- **Queries** never hydrate an aggregate. Lists, gets, access summaries, role members and audit searches
  read projections or DTOs straight from storage. Examples: `LoadUserPort.findView*`,
  `RoleQueryRepository`, the audit `Specification` search. There is no CQRS infrastructure beyond this.
- **Read-side SQL may join across slices.** For example, role members and `/authz/check` join `users`
  and `tenants`. Writes always go through the owning slice.
- **Tenant isolation is enforced at the repository.** Every tenant-scoped repository or port method takes
  the tenant id and filters by it in the same query, e.g. `findById(TenantId, UserId)`. Never load by id
  alone and check the tenant afterwards. `UserRoleRepository` extends `Repository`, not `JpaRepository`,
  so id-only operations don't exist. Its insert and delete match the user and the role against the tenant
  in a single statement.
- **The database is the source of truth for authorization.** `/authz/check` runs one indexed `EXISTS`
  query (target p95 < 50 ms), and uLock's own API resolves permissions per request. JWTs carry identity
  and tenant (`sub`, `tid`). The `roles` claim is informational and may be up to 15 minutes stale, so
  never authorize on it.
- **Audit is transactional.** Audit records are written synchronously in the same transaction as the
  change, either by direct `AuditService` calls (flat slices) or by an event listener (user slice). There
  is no outbox and no async processing in v1.

## Domain rules worth knowing

- **User status.** Transitions are explicit in `UserStatus.canTransitionTo`: only ACTIVE ⇄ INACTIVE.
  Setting the current status again is a no-op. Lockout is not a status: `lockedUntil` is a temporary
  condition.
- **Lockout.** 5 failed logins within 15 minutes lock the account for 15 minutes. These are constants
  on `User`, not configuration, because the ERD fixes them.
- **Email.** Normalised to trimmed lower case by `Email`, unique per tenant and immutable. v1 has no
  change-email requirement.
- **Role names.** Trimmed, with internal whitespace collapsed, 1–100 characters and case preserved. Unique
  per tenant ignoring case (`uq_roles_tenant_name` on `lower(name)`). Reserved names (`PLATFORM_ADMIN`,
  `TENANT_ADMIN`, `TENANT_AUDITOR`) are blocked ignoring case.
- **Permission codes.** Canonical as given, with no trimming or case folding: `^[a-z][a-z0-9_-]*(:[a-z][a-z0-9_-]*)+$`,
  at most 100 characters. Unique per tenant, and may never reuse a system code.
- **Role permissions.** A role may hold only its own tenant's permissions and system permissions.
  Platform-only system permissions are allowed only in the platform tenant. `Role.requireCanHold`
  enforces this.
- **Explicit domain events.** For example `UserRenamed`, `UserDeactivated`, `UserActivated`, never a
  generic `UserUpdated`. Audit actions mirror them.

## Decisions taken in the user-slice pilot

- **Explicit tenant filtering, no Hibernate `@TenantId`.** Every outbound persistence method takes a
  `TenantId`, and every query filters by it. `@TenantId` fixes the tenant per session, which breaks tenant
  creation (a platform admin writes into the new tenant), login (anonymous, tenant comes from the slug) and
  bootstrap. The persistence adapter test covers isolation between tenants.
- **Three inbound ports on user.** `GetUserUseCase`, `CreateUserUseCase` (used by tenant creation and
  bootstrap) and `AuthenticateUserUseCase` (used by login). The password check and the lockout rules live in
  the user domain.
- **One shared event port.** `DomainEventPublisherPort` sits in the shared kernel, so `common` implements
  a single port instead of depending on every slice.
- **Simplified after the pilot.**
  - `LockoutPolicy` became constants on `User`.
  - `UserSnapshot` and MapStruct were replaced by a hand-written mapping in the adapter.
  - `RoleId` was removed until something needs it.
  - The id-list user lookup was dropped: role members is now a single SQL projection.
- **Timestamps and ids.** The domain assigns them, using `Clock` and `UserId.newId()`. The JPA entity has no
  Hibernate timestamp annotations.
- **Auditing through events.** An adapter in the slice (`adapter/out/audit`) listens to its domain events
  and writes the audit records, synchronously and in the same transaction.
- **Paging.** Application code uses `PageQuery`/`PageResult`. Controllers convert with
  `common/web/PageQueries` and `PageResponse.of(PageResult, …)`.
- **Package visibility.** Classes are package-private unless the subpackage grouping forces them to be
  public. ArchUnit, not visibility, guards the slice boundaries.

## Migrating the next slice (checklist)

1. **Plan and wait.** Show the current structure, the planned moves, and everything outside the slice that
   breaks. Wait for approval.
2. **Refactor in small steps.** Keep the build compiling after each step.
3. **Tests.** Write plain-JUnit domain tests, and service tests with mocked ports. Add a Testcontainers
   `@DataJpaTest` for the persistence adapter that includes a tenant-isolation case, and a `@WebMvcTest`
   for the controller.
4. **Verify.** Run `./mvnw verify`, and fix ArchUnit violations in the code, not in the rule.
5. **Update this file.** Record any new decisions here.

## Conventions

- **Tests.** `*Test` for unit and slice tests, `*IntegrationTest` for full-stack tests on
  `support/IntegrationTest`. All of them run with `mvn verify`.
- **Migrations.** Flyway `V<n>__<description>.sql`. Never edit a migration once it has shipped.
- **Commits.** Conventional Commits.
