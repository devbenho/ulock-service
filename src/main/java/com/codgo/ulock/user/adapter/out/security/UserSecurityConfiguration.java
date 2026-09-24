package com.codgo.ulock.user.adapter.out.security;

import com.codgo.ulock.common.security.SecurityProperties;
import com.codgo.ulock.user.domain.policy.LockoutPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class UserSecurityConfiguration {

    @Bean
    LockoutPolicy lockoutPolicy(SecurityProperties properties) {
        return new LockoutPolicy(properties.maxFailedLogins(), properties.failedLoginWindow(),
                properties.lockDuration());
    }
}
