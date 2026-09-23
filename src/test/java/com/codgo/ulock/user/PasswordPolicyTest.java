package com.codgo.ulock.user;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codgo.ulock.common.error.InvalidRequestException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsTenCharacters() {
        assertThatCode(() -> PasswordPolicy.validate("0123456789")).doesNotThrowAnyException();
    }

    @Test
    void rejectsShortAndMissingPasswords() {
        assertThatThrownBy(() -> PasswordPolicy.validate("012345678")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate(null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void countsCharactersNotBytesForTheMinimum() {
        assertThatThrownBy(() -> PasswordPolicy.validate("ééééééééé")).isInstanceOf(InvalidRequestException.class);
        assertThatCode(() -> PasswordPolicy.validate("éééééééééé")).doesNotThrowAnyException();
    }

    @Test
    void rejectsPasswordsBeyondBcryptsSeventyTwoByteLimit() {
        assertThatCode(() -> PasswordPolicy.validate("a".repeat(72))).doesNotThrowAnyException();
        assertThatThrownBy(() -> PasswordPolicy.validate("a".repeat(73))).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("é".repeat(37))).isInstanceOf(InvalidRequestException.class);
    }
}
