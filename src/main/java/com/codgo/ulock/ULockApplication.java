package com.codgo.ulock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ULockApplication {

    public static void main(String[] args) {
        SpringApplication.run(ULockApplication.class, args);
    }
}
