package com.example.footballanalysis.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WorkerArtifactUrlValidator")
class WorkerArtifactUrlValidatorTest {

    @Test
    void allowsPublicAndInternalMinioPrefixes() {
        WorkerArtifactUrlValidator validator = new WorkerArtifactUrlValidator(
                "http://localhost:9000/",
                "http://minio:9000");

        assertThat(validator.requireAllowed("http://localhost:9000/tracking-data/a.json"))
                .isEqualTo("http://localhost:9000/tracking-data/a.json");
        assertThat(validator.requireAllowed("http://minio:9000/hls-streams/a.m3u8"))
                .isEqualTo("http://minio:9000/hls-streams/a.m3u8");
    }

    @Test
    void rejectsForeignHost() {
        WorkerArtifactUrlValidator validator = new WorkerArtifactUrlValidator(
                "http://localhost:9000",
                "http://minio:9000");

        assertThatThrownBy(() -> validator.requireAllowed("https://evil.example/malware.js"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MinIO");
    }

    @Test
    void passesThroughBlankAndNull() {
        WorkerArtifactUrlValidator validator = new WorkerArtifactUrlValidator(
                "http://localhost:9000",
                "http://minio:9000");

        assertThat(validator.requireAllowed(null)).isNull();
        assertThat(validator.requireAllowed("")).isEmpty();
        assertThat(validator.requireAllowed("   ")).isEqualTo("   ");
    }

    @Test
    void skipsCheckWhenNoEndpointsConfigured() {
        WorkerArtifactUrlValidator validator = new WorkerArtifactUrlValidator(List.of());

        assertThat(validator.requireAllowed("https://anywhere.example/file")).isEqualTo("https://anywhere.example/file");
    }

    @Test
    void ignoresBlankConfiguredEndpoints() {
        WorkerArtifactUrlValidator validator = new WorkerArtifactUrlValidator("  ", "");

        assertThat(validator.requireAllowed("https://anywhere.example/file")).isEqualTo("https://anywhere.example/file");
    }
}
