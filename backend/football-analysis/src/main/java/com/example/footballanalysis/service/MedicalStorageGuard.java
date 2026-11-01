package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ExternalServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionRequest;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionResponse;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionRule;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Decides whether new sport medical files may be collected (docs/sportorvosi-jogszeruseg.md, Tárolás). The guarded
 * buckets must have default encryption and must never have had versioning, because a delete in a versioned bucket
 * keeps the old version. With {@code app.medical.strict-storage} the MinIO secret must not be a known dev value and
 * the public endpoint must be HTTPS.
 * <p>
 * Runs once the application is ready and again before the nightly cleanup. A false flag only stops new files:
 * reading, opening and deleting existing ones go on. While the flag is false a refused request checks again at
 * most once a minute, so a MinIO that finished its setup after the backend started is picked up without a restart.
 */
@Component
@Slf4j
public class MedicalStorageGuard {

    /** Secrets that are in the repository: the old shared default and the current dev value. */
    static final Set<String> KNOWN_DEV_SECRETS = Set.of("SuperSecretKey123", "dev-only-medical-app-secret");
    static final Duration RECHECK_AFTER = Duration.ofMinutes(1);

    private final S3Client s3Client;
    private final List<String> buckets;
    private final String secretKey;
    private final String publicEndpoint;
    private final boolean strict;
    private final Clock clock;

    private volatile Result last = new Result(false, List.of("not_checked"));
    private volatile Instant checkedAt = Instant.MIN;

    public MedicalStorageGuard(S3Client s3Client,
                               @Value("${minio.buckets.medical-docs:medical-docs}") String medicalBucket,
                               @Value("${minio.buckets.club-documents:club-documents}") String clubDocumentsBucket,
                               @Value("${minio.secret-key}") String secretKey,
                               @Value("${minio.public-endpoint}") String publicEndpoint,
                               @Value("${app.medical.strict-storage:false}") boolean strict,
                               Clock clock) {
        this.s3Client = s3Client;
        this.buckets = List.of(medicalBucket, clubDocumentsBucket);
        this.secretKey = secretKey;
        this.publicEndpoint = publicEndpoint;
        this.strict = strict;
        this.clock = clock;
    }

    /** After {@link MedicalFileStore#ensureBucket} and {@link ClubDocumentStore#ensureBucket}, so a bucket created there is checked too. */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void checkOnStartup() {
        Result result = check();
        if (result.secure()) {
            log.info("Medical storage is secure");
        } else if (strict) {
            log.error("Medical storage is not secure, new medical files are refused: {}", result.reasons());
        } else {
            log.warn("Medical storage is not secure, new medical files are refused: {}", result.reasons());
        }
    }

    public Result check() {
        List<String> reasons = new ArrayList<>();
        for (String bucket : buckets) {
            checkBucket(bucket, reasons);
        }
        if (strict) {
            if (secretKey == null || secretKey.isBlank() || KNOWN_DEV_SECRETS.contains(secretKey)) {
                reasons.add("dev_secret");
            }
            if (publicEndpoint == null || !publicEndpoint.startsWith("https://")) {
                reasons.add("public_endpoint_not_https");
            }
        }
        Result result = new Result(reasons.isEmpty(), List.copyOf(reasons));
        if (result.secure() != last.secure() && !last.reasons().equals(List.of("not_checked"))) {
            log.warn("Medical storage changed to {}: {}", result.secure() ? "secure" : "not secure", result.reasons());
        }
        last = result;
        checkedAt = clock.instant();
        return result;
    }

    private void checkBucket(String bucket, List<String> reasons) {
        try {
            GetBucketEncryptionResponse encryption = s3Client.getBucketEncryption(
                    GetBucketEncryptionRequest.builder().bucket(bucket).build());
            boolean encrypted = encryption.serverSideEncryptionConfiguration() != null
                    && encryption.serverSideEncryptionConfiguration().rules().stream()
                    .map(ServerSideEncryptionRule::applyServerSideEncryptionByDefault)
                    .anyMatch(rule -> rule != null && rule.sseAlgorithmAsString() != null
                            && !rule.sseAlgorithmAsString().isBlank());
            if (!encrypted) {
                reasons.add("encryption_missing:" + bucket);
            }
        } catch (S3Exception ex) {
            // MinIO answers 404 (ServerSideEncryptionConfigurationNotFoundError) for a bucket without encryption.
            if (ex.statusCode() == 404) {
                reasons.add("encryption_missing:" + bucket);
            } else {
                log.warn("Could not read the encryption of bucket {}: {}", bucket, ex.getMessage());
                reasons.add("unreachable:" + bucket);
            }
            return;
        } catch (RuntimeException ex) {
            log.warn("Could not read the encryption of bucket {}: {}", bucket, ex.getMessage());
            reasons.add("unreachable:" + bucket);
            return;
        }
        try {
            String versioning = s3Client.getBucketVersioning(GetBucketVersioningRequest.builder().bucket(bucket).build())
                    .statusAsString();
            // Only "never versioned" is accepted. Suspended keeps the versions made while it was enabled.
            if (versioning != null && !versioning.isBlank()) {
                reasons.add("versioning_" + versioning.toLowerCase() + ":" + bucket);
            }
        } catch (RuntimeException ex) {
            log.warn("Could not read the versioning of bucket {}: {}", bucket, ex.getMessage());
            reasons.add("unreachable:" + bucket);
        }
    }

    public boolean isSecure() {
        return last.secure();
    }

    /** The last result, with reason codes only (bucket names, never keys or secrets). */
    public Result lastResult() {
        return last;
    }

    /** Throws the 503 {@code error.medical.storage_not_secure} when new medical files must not be collected. */
    public void requireSecure() {
        Result result = last;
        if (!result.secure() && !checkedAt.isAfter(clock.instant().minus(RECHECK_AFTER))) {
            result = check();
        }
        if (!result.secure()) {
            throw new ExternalServiceException("error.medical.storage_not_secure", new Object[0],
                    "New medical files are refused because the file storage is not secured: " + result.reasons());
        }
    }

    public record Result(boolean secure, List<String> reasons) {}
}
