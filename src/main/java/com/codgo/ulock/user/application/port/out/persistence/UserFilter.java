package com.codgo.ulock.user.application.port.out.persistence;

import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.domain.model.UserStatus;
import java.util.Set;

/** Optional criteria for {@link LoadUserPort#loadUsers}; null means "no restriction". */
public record UserFilter(UserStatus status, Set<UserId> ids) {

    public static UserFilter none() {
        return new UserFilter(null, null);
    }

    public static UserFilter byStatus(UserStatus status) {
        return new UserFilter(status, null);
    }

    public static UserFilter byIds(Set<UserId> ids) {
        return new UserFilter(null, Set.copyOf(ids));
    }
}
