package com.example.footballanalysis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Application clock. Event times are stored as local date-times in the club's zone,
 * so "now" must be read in that same zone instead of the JVM/container default.
 */
@Configuration
public class ClockConfig {

    private static final String DEFAULT_ZONE = "Europe/Budapest";

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock clock(@Value("${app.time-zone:Europe/Budapest}") String timeZone) {
        // The default above covers only a missing variable; an empty APP_TIME_ZONE= would make ZoneId.of fail.
        String zone = timeZone == null || timeZone.isBlank() ? DEFAULT_ZONE : timeZone.trim();
        return Clock.system(ZoneId.of(zone));
    }
}
