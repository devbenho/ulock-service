package com.codgo.ulock.common;

import java.util.UUID;

/** The reserved tenant hosting platform administrators, seeded by {@code V2__seed_system_data.sql}. */
public final class PlatformTenant {

    public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private PlatformTenant() {}

    public static boolean is(UUID tenantId) {
        return ID.equals(tenantId);
    }
}
