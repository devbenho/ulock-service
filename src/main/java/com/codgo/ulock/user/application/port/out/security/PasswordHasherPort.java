package com.codgo.ulock.user.application.port.out.security;

public interface PasswordHasherPort {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);

    /** Spends the time of a real comparison, so responses do not reveal whether an account exists. */
    void simulateMatch(String rawPassword);
}
