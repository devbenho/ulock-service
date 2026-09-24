package com.codgo.ulock.sharedkernel.valueobject;

import java.util.Objects;
import java.util.UUID;

public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "tenant id");
    }

    public static TenantId of(UUID value) {
        return new TenantId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
