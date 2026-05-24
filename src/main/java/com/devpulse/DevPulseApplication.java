package com.devpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DevPulse - GitHub Repository Health Monitor
 *
 * Automatically scrapes GitHub repositories, scores their delivery health,
 * and alerts teams when metrics cross thresholds.
 */
@SpringBootApplication
@EnableScheduling
public class DevPulseApplication {
    public static void main(String[] args) {
        SpringApplication.run(DevPulseApplication.class, args);
    }
}
