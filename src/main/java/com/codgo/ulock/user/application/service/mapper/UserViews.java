package com.codgo.ulock.user.application.service.mapper;

import com.codgo.ulock.user.application.port.in.model.UserView;
import com.codgo.ulock.user.domain.model.User;

public final class UserViews {

    private UserViews() {}

    public static UserView from(User user) {
        return new UserView(user.id(), user.tenantId(), user.email(), user.fullName(), user.status().name(),
                user.isActive());
    }
}
