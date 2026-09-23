package com.codgo.ulock.role;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record UserRoleId(UUID userId, UUID roleId) implements Serializable {}
