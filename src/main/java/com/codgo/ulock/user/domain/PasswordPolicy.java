package com.codgo.ulock.user.domain;

import com.codgo.ulock.user.domain.InvalidPasswordException;
import java.nio.charset.StandardCharsets;

/** Password rules: at least 10 characters, and within BCrypt's 72-byte input limit. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {}

    public static void validate(String password) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            throw new InvalidPasswordException("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new InvalidPasswordException("Password must be at most " + MAX_BYTES + " bytes");
        }
    }
}
