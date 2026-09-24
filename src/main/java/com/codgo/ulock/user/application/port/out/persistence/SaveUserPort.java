package com.codgo.ulock.user.application.port.out.persistence;

import com.codgo.ulock.user.domain.model.User;

public interface SaveUserPort {

    /** Inserts or updates the user. */
    void save(User user);
}
