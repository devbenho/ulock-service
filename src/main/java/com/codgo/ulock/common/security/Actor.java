package com.codgo.ulock.common.security;

import java.util.UUID;

/**
 * The authenticated caller. For users both ids are set; for machine clients both are null and
 * {@code clientId} is set.
 */
public record Actor(UUID userId, UUID tenantId, String clientId) {}
