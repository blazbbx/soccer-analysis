package com.example.footballanalysis.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled}. The only scheduled task is the nightly sport medical cleanup. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
