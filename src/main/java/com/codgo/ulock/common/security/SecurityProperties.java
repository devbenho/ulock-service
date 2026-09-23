package com.codgo.ulock.common.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ulock.security")
public record SecurityProperties(
        int bcryptStrength,
        int maxFailedLogins,
        Duration failedLoginWindow,
        Duration lockDuration,
        List<String> corsAllowedOrigins) {}
