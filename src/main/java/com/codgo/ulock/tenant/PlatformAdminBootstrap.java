package com.codgo.ulock.tenant;

import com.codgo.ulock.common.PlatformTenant;
import com.codgo.ulock.common.error.ConflictException;
import com.codgo.ulock.role.RoleAssignmentService;
import com.codgo.ulock.role.ReservedRole;
import com.codgo.ulock.user.User;
import com.codgo.ulock.user.UserDtos.CreateUserRequest;
import com.codgo.ulock.user.UserRepository;
import com.codgo.ulock.user.UserService;
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
    private final UserRepository users;
    private final UserService userService;
    private final RoleAssignmentService roleAssignments;
    private final TransactionTemplate transaction;

    PlatformAdminBootstrap(BootstrapProperties properties, UserRepository users, UserService userService,
                           RoleAssignmentService roleAssignments, PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.users = users;
        this.userService = userService;
        this.roleAssignments = roleAssignments;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByTenantId(PlatformTenant.ID)) {
            return;
        }
        if (!StringUtils.hasText(properties.adminEmail()) || !StringUtils.hasText(properties.adminPassword())) {
            log.warn("No platform administrator exists and ulock.bootstrap.admin-email/admin-password are not set; "
                    + "no one can manage tenants until one is configured");
            return;
        }
        try {
            transaction.executeWithoutResult(status -> {
                User admin = userService.create(PlatformTenant.ID, new CreateUserRequest(
                        properties.adminEmail(), properties.adminFullName(), properties.adminPassword()));
                roleAssignments.assignReserved(PlatformTenant.ID, admin.getId(), ReservedRole.PLATFORM_ADMIN);
            });
            log.info("Bootstrapped the platform administrator");
        } catch (DataIntegrityViolationException | ConflictException alreadyCreated) {
            // Another instance starting at the same time won the race.
            log.info("Platform administrator was bootstrapped by another instance");
        }
    }
}
