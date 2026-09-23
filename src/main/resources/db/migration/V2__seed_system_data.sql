-- Built-in system permissions (tenant_id NULL). uLock guards its own API with these.
-- Keep in sync with com.codgo.ulock.role.SystemPermission.
INSERT INTO permissions (id, tenant_id, code, description, created_at, updated_at) VALUES
    ('00000000-0000-0000-0001-000000000001', NULL, 'tenant:read',   'View tenants (platform only)',            now(), now()),
    ('00000000-0000-0000-0001-000000000002', NULL, 'tenant:write',  'Create and update tenants (platform only)', now(), now()),
    ('00000000-0000-0000-0001-000000000003', NULL, 'client:manage', 'Manage machine clients (platform only)',  now(), now()),
    ('00000000-0000-0000-0001-000000000004', NULL, 'user:read',     'View users',                              now(), now()),
    ('00000000-0000-0000-0001-000000000005', NULL, 'user:write',    'Create, update and reset users',          now(), now()),
    ('00000000-0000-0000-0001-000000000006', NULL, 'role:read',     'View roles, permissions and access',      now(), now()),
    ('00000000-0000-0000-0001-000000000007', NULL, 'role:write',    'Manage roles, permissions and assignments', now(), now()),
    ('00000000-0000-0000-0001-000000000008', NULL, 'audit:read',    'View audit logs',                         now(), now());

-- The reserved platform tenant hosts platform administrators.
INSERT INTO tenants (id, name, slug, status, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Platform', 'platform', 'ACTIVE', now(), now());

INSERT INTO roles (id, tenant_id, name, description, created_at, updated_at) VALUES
    ('00000000-0000-0000-0002-000000000001', '00000000-0000-0000-0000-000000000001',
     'PLATFORM_ADMIN', 'Manages tenants and machine clients', now(), now());

INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0002-000000000001', id FROM permissions WHERE tenant_id IS NULL;
