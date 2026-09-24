package com.codgo.ulock.user.application;

import com.codgo.ulock.user.domain.User;

public interface SaveUserPort {

    /** Inserts or updates the user. */
    void save(User user);
}
