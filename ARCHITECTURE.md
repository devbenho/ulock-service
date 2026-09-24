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
time, it can overwrite `target/classes` without annotation processing, which breaks MapStruct
mappers. The symptom is "No qualifying bean of type …Mapper". Stop the IDE build or run Maven from a
copy of the tree.

## Target architecture

The codebase uses vertical slices, and each slice is its own hexagon. There is also a small shared
kernel. The base package is `com.codgo.ulock`.

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
│   │   ├── model/                aggregate root, its snapshot, enums, value types
│   │   ├── policy/               business rules that are not an entity (LockoutPolicy, PasswordPolicy)
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
   returned from controllers and never passed into the domain. Map them to and from the domain snapshot
   with MapStruct.
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
