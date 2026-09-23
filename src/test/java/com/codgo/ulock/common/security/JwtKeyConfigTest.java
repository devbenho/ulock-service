package com.codgo.ulock.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtKeyConfigTest {

    private final JwtKeyConfig config = new JwtKeyConfig();

    @Test
    void loadsAConfiguredPemKeyAndDerivesItsPublicHalf() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair expected = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(expected.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        RSAKey key = config.signingKey(properties(pem, false));

        assertThat(key.toRSAPublicKey()).isEqualTo(expected.getPublic());
        assertThat(key.getKeyID()).isEqualTo("kid-1");
        assertThat(key.isPrivate()).isTrue();
    }

    @Test
    void refusesToStartWithoutAKeyUnlessEphemeralKeysAreAllowed() {
        assertThatThrownBy(() -> config.signingKey(properties("", false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ULOCK_JWT_PRIVATE_KEY");
    }

    @Test
    void generatesAnEphemeralKeyWhenExplicitlyAllowed() throws Exception {
        RSAKey key = config.signingKey(properties("", true));

        assertThat(key.isPrivate()).isTrue();
        assertThat(key.size()).isEqualTo(2048);
    }

    private static JwtProperties properties(String pem, boolean allowEphemeralKey) {
        return new JwtProperties("https://issuer.test", Duration.ofMinutes(15), Duration.ofDays(7), "kid-1", pem,
                allowEphemeralKey);
    }
}
