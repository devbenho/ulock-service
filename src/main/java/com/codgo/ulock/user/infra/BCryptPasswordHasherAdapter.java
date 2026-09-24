package com.codgo.ulock.user.infra;

import com.codgo.ulock.user.application.PasswordHasherPort;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Hashes with the application's BCrypt {@link PasswordEncoder} (cost from {@code ulock.security.bcrypt-strength}). */
@Component
class BCryptPasswordHasherAdapter implements PasswordHasherPort {

    private final PasswordEncoder encoder;
    private final String dummyHash;

    BCryptPasswordHasherAdapter(PasswordEncoder encoder) {
        this.encoder = encoder;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }

    @Override
    public void simulateMatch(String rawPassword) {
        encoder.matches(rawPassword, dummyHash);
    }
}
