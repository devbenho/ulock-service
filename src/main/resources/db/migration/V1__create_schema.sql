-- uLock v1 schema. All primary keys are UUIDs; all timestamps are timestamptz in UTC.

CREATE TABLE tenants (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    slug        VARCHAR(63)  NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_tenants_slug UNIQUE (slug),
    CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE users (
    id                    UUID         PRIMARY KEY,
    tenant_id             UUID         NOT NULL REFERENCES tenants (id),
    email                 VARCHAR(320) NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    full_name             VARCHAR(200) NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    last_login_at         TIMESTAMPTZ,
    failed_login_count    INTEGER      NOT NULL DEFAULT 0,
    failed_login_window_start  TIMESTAMPTZ,
    locked_until          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE roles (
    id           UUID         PRIMARY KEY,
    tenant_id    UUID         NOT NULL REFERENCES tenants (id),
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL
);

-- Role names are unique per tenant, case-insensitively.
CREATE UNIQUE INDEX uq_roles_tenant_name ON roles (tenant_id, lower(name));

-- tenant_id NULL marks a built-in system permission.
CREATE TABLE permissions (
    id           UUID         PRIMARY KEY,
    tenant_id    UUID         REFERENCES tenants (id),
    code         VARCHAR(100) NOT NULL,
    description  VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_permissions_tenant_code UNIQUE (tenant_id, code)
);

-- UNIQUE treats NULLs as distinct, so system permission codes need their own index.
CREATE UNIQUE INDEX uq_permissions_system_code ON permissions (code) WHERE tenant_id IS NULL;

CREATE TABLE role_permissions (
    role_id        UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id  UUID NOT NULL REFERENCES permissions (id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX ix_role_permissions_permission ON role_permissions (permission_id);

CREATE TABLE user_roles (
    user_id      UUID        NOT NULL REFERENCES users (id),
    role_id      UUID        NOT NULL REFERENCES roles (id),
    assigned_by  UUID        REFERENCES users (id),
    assigned_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX ix_user_roles_role ON user_roles (role_id);

-- Opaque refresh tokens, stored as SHA-256 hashes and rotated on every use.
CREATE TABLE refresh_tokens (
    id                     UUID         PRIMARY KEY,
    user_id                UUID         NOT NULL REFERENCES users (id),
    tenant_id              UUID         NOT NULL REFERENCES tenants (id),
    token_hash             VARCHAR(64)  NOT NULL,
    issued_at              TIMESTAMPTZ  NOT NULL,
    expires_at             TIMESTAMPTZ  NOT NULL,
    revoked_at             TIMESTAMPTZ,
    replaced_by_token_id   UUID         REFERENCES refresh_tokens (id),
    ip_address             VARCHAR(45),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

-- One machine client per external system; authenticates with the client-credentials grant.
CREATE TABLE machine_clients (
    id                  UUID         PRIMARY KEY,
    client_id           VARCHAR(100) NOT NULL,
    name                VARCHAR(200) NOT NULL,
    client_secret_hash  VARCHAR(100) NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_machine_clients_client_id UNIQUE (client_id),
    CONSTRAINT ck_machine_clients_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE audit_logs (
    id             UUID         PRIMARY KEY,
    tenant_id      UUID         REFERENCES tenants (id),
    actor_user_id  UUID         REFERENCES users (id),
    action         VARCHAR(64)  NOT NULL,
    target_type    VARCHAR(64),
    target_id      VARCHAR(100),
    details        JSONB,
    ip_address     VARCHAR(45),
    created_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_audit_logs_tenant_created ON audit_logs (tenant_id, created_at);
CREATE INDEX ix_audit_logs_actor_created ON audit_logs (actor_user_id, created_at);

-- Audit records are append-only: reject every UPDATE and DELETE at the database level.
-- A future retention purge must run as a migration or maintenance job that disables this trigger.
CREATE FUNCTION audit_logs_reject_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_reject_modification();
