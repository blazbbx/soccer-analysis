package com.example.footballanalysis.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Worker completed messages carry HLS / tracking URLs that the SPA later loads.
 * Reject anything that is not our MinIO public or internal endpoint so a forged
 * queue message cannot point the browser at an attacker host.
 */
@Component
public class WorkerArtifactUrlValidator {

    private final List<String> allowedPrefixes;

    @Autowired
    public WorkerArtifactUrlValidator(
            @Value("${minio.public-endpoint:}") String publicEndpoint,
            @Value("${minio.endpoint:}") String internalEndpoint) {
        this.allowedPrefixes = Stream.of(publicEndpoint, internalEndpoint)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.endsWith("/") ? value.substring(0, value.length() - 1) : value)
                .distinct()
                .toList();
    }

    WorkerArtifactUrlValidator(List<String> allowedPrefixes) {
        this.allowedPrefixes = allowedPrefixes == null ? List.of() : List.copyOf(allowedPrefixes);
    }

    public String requireAllowed(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        if (allowedPrefixes.isEmpty()) {
            return url;
        }
        for (String prefix : allowedPrefixes) {
            if (url.startsWith(prefix)) {
                return url;
            }
        }
        throw new IllegalArgumentException("Worker artifact URL is not from the configured MinIO endpoint.");
    }
}
