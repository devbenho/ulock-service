package com.codgo.ulock.user.domain;

/**
 * Administrative status of a user. Being locked out after failed logins is a separate, temporary
 * condition ({@link User#isLocked}) and not a status.
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE;

    /**
     * The allowed transitions. Keeping the current status is a no-op, not a transition. Adding a
     * status forces a decision here: the switch has no default.
     */
    public boolean canTransitionTo(UserStatus target) {
        return switch (this) {
            case ACTIVE -> target == INACTIVE;
            case INACTIVE -> target == ACTIVE;
        };
    }
}
