package com.example.footballanalysis.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Worker completed messages carry HLS / tracking / image URLs that the SPA later loads.
 * Reject anything that is not our MinIO public or internal endpoint so a forged
 * queue message cannot point the browser at an attacker host.
 *
 * <p>The URL is parsed, not prefix-matched: {@code http://localhost:9000.evil.com/x} and
 * {@code http://localhost:9000@evil.com/x} start with {@code http://localhost:9000} but go elsewhere.
 * Scheme, host and port must equal an allowed endpoint's, and user info is refused.
 */
@Component
public class WorkerArtifactUrlValidator {

    private final List<Endpoint> allowedEndpoints;

    @Autowired
    public WorkerArtifactUrlValidator(
            @Value("${minio.public-endpoint:}") String publicEndpoint,
            @Value("${minio.endpoint:}") String internalEndpoint) {
        this(Stream.of(publicEndpoint, internalEndpoint).toList());
    }

    WorkerArtifactUrlValidator(List<String> allowedEndpoints) {
        this.allowedEndpoints = (allowedEndpoints == null ? List.<String>of() : allowedEndpoints).stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Endpoint::parse)
                .distinct()
                .toList();
    }

    public String requireAllowed(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        if (allowedEndpoints.isEmpty()) {
            return url;
        }
        Endpoint candidate;
        try {
            candidate = Endpoint.parse(url.trim());
        } catch (IllegalArgumentException ex) {
            throw rejected();
        }
        if (candidate.hasUserInfo() || !allowedEndpoints.contains(candidate.withoutUserInfo())) {
            throw rejected();
        }
        return url;
    }

    private static IllegalArgumentException rejected() {
        return new IllegalArgumentException("Worker artifact URL is not from the configured MinIO endpoint.");
    }

    private record Endpoint(String scheme, String host, int port, boolean hasUserInfo) {

        static Endpoint parse(String value) {
            URI uri;
            try {
                uri = new URI(value);
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException("Invalid URL", ex);
            }
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                throw new IllegalArgumentException("URL needs a scheme and a host");
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            int port = uri.getPort() != -1 ? uri.getPort() : defaultPort(scheme);
            return new Endpoint(scheme, host.toLowerCase(Locale.ROOT), port, uri.getRawUserInfo() != null);
        }

        Endpoint withoutUserInfo() {
            return new Endpoint(scheme, host, port, false);
        }

        private static int defaultPort(String scheme) {
            return switch (scheme) {
                case "http" -> 80;
                case "https" -> 443;
                default -> -1;
            };
        }
    }
}
