package com.codgo.ulock.sharedkernel.valueobject;

import java.util.Objects;
import java.util.UUID;

public record RoleId(UUID value) {

    public RoleId {
        Objects.requireNonNull(value, "role id");
    }

    public static RoleId of(UUID value) {
        return new RoleId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
