package com.codgo.ulock.common.security;

import java.util.UUID;

/** Published when a caller targets a tenant other than the one in their token. */
public record TenantAccessDeniedEvent(Actor actor, UUID requestedTenantId, String method, String path, String ipAddress) {}
