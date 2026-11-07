package com.smartbus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;

/**
 * Smart School and College Bus Tracking System
 * Main Spring Boot Application Entry Point
 */
@SpringBootApplication
public class SmartBusApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartBusApplication.class, args);
    }

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
