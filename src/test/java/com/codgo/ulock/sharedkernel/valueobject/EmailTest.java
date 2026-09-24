package com.codgo.ulock.sharedkernel.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codgo.ulock.sharedkernel.exception.DomainException;
import org.junit.jupiter.api.Test;

class EmailTest {

    @Test
    void normalisesToTrimmedLowerCase() {
        assertThat(Email.of("  Mixed@Case.TEST ")).isEqualTo(Email.of("mixed@case.test"));
    }

    @Test
    void rejectsMalformedAddressesAsInvalidDomainInput() {
        assertThatThrownBy(() -> Email.of("not-an-email"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.category()).isEqualTo(DomainException.Category.INVALID));
        assertThatThrownBy(() -> Email.of("a@" + "b".repeat(Email.MAX_LENGTH))).isInstanceOf(DomainException.class);
    }

    @Test
    void tryParseReturnsEmptyInsteadOfThrowing() {
        assertThat(Email.tryParse("nope")).isEmpty();
        assertThat(Email.tryParse(null)).isEmpty();
        assertThat(Email.tryParse("A@B.test")).contains(Email.of("a@b.test"));
    }
}
