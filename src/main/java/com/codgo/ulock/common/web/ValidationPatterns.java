package com.codgo.ulock.common.web;

public final class ValidationPatterns {

    /** At least one non-whitespace character. For optional fields, where {@code @NotBlank} would reject null. */
    public static final String NOT_BLANK = "(?s).*\\S.*";

    private ValidationPatterns() {}
}
