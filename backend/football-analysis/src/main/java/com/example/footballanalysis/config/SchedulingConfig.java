package com.example.footballanalysis.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled}: the nightly sport medical cleanup and the team chat heartbeat. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
