package com.codgo.ulock.sharedkernel.valueobject;

import com.codgo.ulock.sharedkernel.exception.DomainException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** An email address, normalised to lower case so equality is case-insensitive. */
public record Email(String value) {

    public static final int MAX_LENGTH = 320;
    private static final Pattern SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    public Email {
        Objects.requireNonNull(value, "email");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH || !SHAPE.matcher(value).matches()) {
            throw new InvalidEmailException(value);
        }
    }

    public static Email of(String value) {
        return new Email(value);
    }

    /** Empty instead of throwing when {@code value} is not a valid address. */
    public static Optional<Email> tryParse(String value) {
        try {
            return value == null ? Optional.empty() : Optional.of(new Email(value));
        } catch (InvalidEmailException invalid) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return value;
    }

    public static final class InvalidEmailException extends DomainException {

        InvalidEmailException(String value) {
            super(Category.INVALID, "Invalid email address: " + value);
        }
    }
}
