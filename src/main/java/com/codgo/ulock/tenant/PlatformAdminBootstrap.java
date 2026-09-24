package com.codgo.ulock.tenant;

import com.codgo.ulock.common.PlatformTenant;
import com.codgo.ulock.role.ReservedRole;
import com.codgo.ulock.role.RoleAssignmentService;
import com.codgo.ulock.sharedkernel.exception.DomainException;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.user.api.UserApi;
import com.codgo.ulock.user.api.CreateUserCommand;
import com.codgo.ulock.user.api.UserView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Creates the first PLATFORM_ADMIN from {@code ulock.bootstrap.*} when the platform tenant has no
 * users yet. Does nothing once any platform user exists.
 */
@Component
class PlatformAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

    private final BootstrapProperties properties;
    private final UserApi users;
    private final RoleAssignmentService roleAssignments;
    private final TransactionTemplate transaction;

    PlatformAdminBootstrap(BootstrapProperties properties, UserApi users,
                           RoleAssignmentService roleAssignments, PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.users = users;
        this.roleAssignments = roleAssignments;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.hasUsers(TenantId.of(PlatformTenant.ID))) {
            return;
        }
        if (!StringUtils.hasText(properties.adminEmail()) || !StringUtils.hasText(properties.adminPassword())) {
            log.warn("No platform administrator exists and ulock.bootstrap.admin-email/admin-password are not set; "
                    + "no one can manage tenants until one is configured");
            return;
        }
        try {
            transaction.executeWithoutResult(status -> {
                UserView admin = users.createUser(new CreateUserCommand(TenantId.of(PlatformTenant.ID),
                        properties.adminEmail(), properties.adminFullName(), properties.adminPassword()));
                roleAssignments.assignReserved(PlatformTenant.ID, admin.id().value(), ReservedRole.PLATFORM_ADMIN);
            });
            log.info("Bootstrapped the platform administrator");
        } catch (DataIntegrityViolationException alreadyCreated) {
            logLostRace();
        } catch (DomainException rejected) {
            if (rejected.category() != DomainException.Category.CONFLICT) {
                throw rejected;
            }
            logLostRace();
        }
    }

    /** Another instance starting at the same time created the administrator first. */
    private static void logLostRace() {
        log.info("Platform administrator was bootstrapped by another instance");
    }
}
