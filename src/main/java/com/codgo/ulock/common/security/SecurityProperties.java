package com.codgo.ulock.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ulock.security")
public record SecurityProperties(int bcryptStrength, List<String> corsAllowedOrigins) {}
