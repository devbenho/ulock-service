package com.codgo.ulock.user.adapter.out.persistence.mapper;

import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.adapter.out.persistence.entity.UserJpaEntity;
import com.codgo.ulock.user.domain.model.UserSnapshot;
import java.util.UUID;
import org.mapstruct.Mapper;

/** Maps between the domain's {@link UserSnapshot} and the {@link UserJpaEntity} row. */
@Mapper
public interface UserPersistenceMapper {

    UserJpaEntity toEntity(UserSnapshot snapshot);

    UserSnapshot toSnapshot(UserJpaEntity entity);

    default UUID fromUserId(UserId id) {
        return id.value();
    }

    default UserId toUserId(UUID id) {
        return UserId.of(id);
    }

    default UUID fromTenantId(TenantId id) {
        return id.value();
    }

    default TenantId toTenantId(UUID id) {
        return TenantId.of(id);
    }

    default String fromEmail(Email email) {
        return email.value();
    }

    default Email toEmail(String email) {
        return Email.of(email);
    }
}
