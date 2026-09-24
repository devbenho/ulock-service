package com.codgo.ulock.user.domain;

/** Outcome of {@link User#recordLoginAttempt}. {@code lockedNow} is true if this attempt locked the account. */
public record LoginAttempt(Result result, boolean lockedNow) {

    public enum Result {
        SUCCEEDED,
        BAD_PASSWORD,
        LOCKED,
        INACTIVE
    }

    public boolean succeeded() {
        return result == Result.SUCCEEDED;
    }
}
