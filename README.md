# ulock-service

uLock is the platform's multi-tenant access control service. It authenticates users, issues tokens, and answers
"is this user allowed to do this?" with flat RBAC. The design is in
[uLock Service — Engineering Requirements Document](uLock%20Service%20—%20Engineering%20Requirements%20Document.md).

Stack: Java 21, Spring Boot 3.5, Spring Security (OAuth2 resource server + Nimbus JOSE), Spring Data JPA, Flyway,
PostgreSQL 16.

## Running locally

```bash
docker compose up -d        # PostgreSQL 16 on localhost:5432 (ulock_db / ulock / ulock); ULOCK_DB_PORT overrides
ULOCK_JWT_ALLOW_EPHEMERAL_KEY=true \
ULOCK_BOOTSTRAP_ADMIN_EMAIL=admin@example.com \
ULOCK_BOOTSTRAP_ADMIN_PASSWORD='change-me-please' \
./mvnw spring-boot:run
```

- OpenAPI UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health (public). Metrics and `/actuator/prometheus` require a platform
  admin token (`tenant:read`).
- JWKS: http://localhost:8080/.well-known/jwks.json

Startup fails without `ULOCK_JWT_PRIVATE_KEY`, unless `ULOCK_JWT_ALLOW_EPHEMERAL_KEY=true` is set. That setting
generates a throwaway key, which is fine for local development only: tokens won't survive a restart or verify across
instances.

## Tests

```bash
./mvnw verify        # unit + Testcontainers integration tests, then the 80% JaCoCo line-coverage gate
```

Docker must be running for the integration tests. The coverage report is written to `target/site/jacoco/index.html`.

## Configuration

| Variable | Purpose | Default |
| --- | --- | --- |
| `ULOCK_DB_URL`, `ULOCK_DB_USERNAME`, `ULOCK_DB_PASSWORD` | PostgreSQL connection | `jdbc:postgresql://localhost:5432/ulock_db`, `ulock`, `ulock` |
| `ULOCK_JWT_PRIVATE_KEY` | PEM PKCS#8 RSA private key used for RS256 signing; the public key is derived from it | ephemeral |
| `ULOCK_JWT_ALLOW_EPHEMERAL_KEY` | Generate a throwaway signing key when no private key is set (development only) | `false` |
| `ULOCK_JWT_KEY_ID` | `kid` in token headers and JWKS | `ulock-key-1` |
| `ULOCK_JWT_ISSUER` | `iss` claim, validated on every request | `https://ulock.local` |
| `ULOCK_CORS_ALLOWED_ORIGINS` | Comma-separated admin UI origins | `http://localhost:3000` |
| `ULOCK_BOOTSTRAP_ADMIN_EMAIL`, `ULOCK_BOOTSTRAP_ADMIN_PASSWORD` | First platform admin, created only while the platform tenant has no users | unset |
| `ULOCK_TRACING_SAMPLING` | Trace sampling probability | `0.1` |

Generate a signing key with `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048`.

## How it fits together

Code is organised in vertical slices under `com.codgo.ulock`: `auth`, `tenant`, `user`, `role` and `audit`, plus
`common` for errors, paging and security plumbing.

### Tokens

- **User access token:** RS256 JWT, 15 minutes, with claims `sub` (userId), `tid` (tenantId) and `roles` (role names).
  Other services verify it locally against the JWKS. It proves identity and tenant, not permissions: `roles` is
  informational and can be stale. The authorization source of truth is `/authz/check`, which reads the database.
- **Refresh token:** opaque, 7 days, stored as a SHA-256 hash, and rotated on every `/auth/refresh`. Presenting a
  rotated token again revokes all of that user's sessions and is audited as `REFRESH_TOKEN_REUSED`.
- **Machine-client token:** issued by the OAuth 2.0 client-credentials grant at `POST /api/v1/auth/token`
  (form-encoded `grant_type`, `client_id`, `client_secret`). It carries only the scope `authz:check`.

### uLock's own authorization

uLock guards its own API with the same RBAC it offers other services. These system permissions are seeded:

| Permission | Granted by |
| --- | --- |
| `tenant:read`, `tenant:write`, `client:manage` | `PLATFORM_ADMIN` only. These are platform-only and can never be granted outside the platform tenant. |
| `user:read`, `user:write`, `role:read`, `role:write` | `TENANT_ADMIN` |
| `audit:read` | `TENANT_ADMIN`, `TENANT_AUDITOR` |

- **Reserved roles.** Every new tenant gets `TENANT_ADMIN` and `TENANT_AUDITOR`. These roles can't be renamed,
  re-permissioned or deleted. `PLATFORM_ADMIN` lives in the reserved `platform` tenant.
- **Authorities come from the database.** They are loaded on every request, so revoking a role, deactivating a user or
  suspending a tenant takes effect at once rather than when the token expires.
- **No privilege escalation.** A caller can only grant or revoke system permissions they hold themselves, and can
  only reset the password of, or deactivate, a user whose system permissions they also hold. So a helpdesk role
  with `user:write` can't take over a `TENANT_ADMIN`, and a `role:write` holder can't hand out `TENANT_ADMIN`.
  Tenant-defined business permissions are exempt from this rule.
- **Tenant isolation.** On every `/api/v1/tenants/{tenantId}/...` resource, the token's `tid` must equal the path
  `tenantId`. Otherwise the request gets 403 and is audited as `TENANT_ACCESS_DENIED`. This applies to platform admins
  as well.

### Implementation decisions beyond the ERD

- **Additions to the data model.** `refresh_tokens` and `machine_clients` tables are added, plus lockout columns on
  `users`: `failed_login_count`, `failed_login_window_start` and `locked_until`.
- **Login.** Login takes `tenantSlug`, `email` and `password`. Emails are matched case-insensitively.
- **Tenant creation.** `POST /tenants` requires an initial `admin` user, who becomes the tenant's first
  `TENANT_ADMIN`. Tenant isolation stops platform admins from creating users inside other tenants, so the first
  admin has to come from here.
- **Password reset.** Admins reset a user's password with `POST /tenants/{tenantId}/users/{userId}/password-reset`.
  A reset, or deactivating the user, revokes all of that user's refresh tokens and lifts any lockout.
- **Role members.** `GET /tenants/{tenantId}/roles/{roleId}/members` lists a role's members (FR-6).
- **Lockout.** An account locks after 5 failed logins within a 15-minute window that starts at the first failure. The
  lock lasts 15 minutes.
- **Role deletion.** Deleting a role that still has members returns 409.
- **Audit logs.** Audit logs are append-only, enforced by a database trigger. There is no purge job in v1, which meets
  the 1-year minimum retention. A future purge must disable the trigger in a controlled maintenance job.
- **Adding a system permission.** Adding one needs a migration that also grants it to the existing reserved roles
  (see `SystemPermission`).

### Known follow-ups

- **Rate limiting.** Login, refresh and token endpoints have no rate limit. Per-IP limits belong at the gateway.
  The hard 5-failure lock also lets anyone who knows an email keep that account locked out, so consider an app-side
  mitigation such as progressive delays.
- **Stable error types.** Problem responses use `type: about:blank`. Stable per-error `type` URIs would let clients
  branch on the error.
- **Refresh-token cleanup.** Expired `refresh_tokens` rows are never deleted. They need a periodic cleanup job.
