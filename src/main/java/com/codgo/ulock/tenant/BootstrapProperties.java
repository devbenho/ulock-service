package com.codgo.ulock.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ulock.bootstrap")
record BootstrapProperties(String adminEmail, String adminPassword, String adminFullName) {}
