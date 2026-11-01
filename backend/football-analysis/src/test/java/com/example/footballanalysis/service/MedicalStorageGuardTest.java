package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ExternalServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionRequest;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionResponse;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionByDefault;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionConfiguration;
import software.amazon.awssdk.services.s3.model.ServerSideEncryptionRule;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MedicalStorageGuardTest {

    private static final String BUCKET = "medical-docs";
    private static final String CLUB_DOCUMENTS = "club-documents";
    private static final String UNIQUE_SECRET = "a-unique-deploy-secret";
    private static final String HTTPS = "https://minio.example.org";
    private static final String HTTP = "http://localhost:9015";

    @Mock private S3Client s3Client;

    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-29T10:00:00Z"));
        encrypted();
        when(s3Client.getBucketVersioning(any(GetBucketVersioningRequest.class)))
                .thenReturn(GetBucketVersioningResponse.builder().build());
    }

    @Test
    void anEncryptedBucketThatWasNeverVersionedIsSecureInBothModes() {
        assertThat(guard(false, UNIQUE_SECRET, HTTP).check().secure()).isTrue();
        assertThat(guard(true, UNIQUE_SECRET, HTTPS).check().secure()).isTrue();
    }

    @Test
    void aBucketWithoutEncryptionIsNotSecure() {
        // MinIO answers 404 ServerSideEncryptionConfigurationNotFoundError.
        when(s3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());

        var result = guard(false, UNIQUE_SECRET, HTTP).check();

        assertThat(result.secure()).isFalse();
        assertThat(result.reasons()).containsExactly("encryption_missing:" + BUCKET, "encryption_missing:" + CLUB_DOCUMENTS);
    }

    @Test
    void anEnabledOrSuspendedVersioningIsNotSecure() {
        when(s3Client.getBucketVersioning(any(GetBucketVersioningRequest.class)))
                .thenReturn(GetBucketVersioningResponse.builder().status(BucketVersioningStatus.ENABLED).build());
        assertThat(guard(false, UNIQUE_SECRET, HTTP).check().reasons()).containsExactly("versioning_enabled:" + BUCKET, "versioning_enabled:" + CLUB_DOCUMENTS);

        when(s3Client.getBucketVersioning(any(GetBucketVersioningRequest.class)))
                .thenReturn(GetBucketVersioningResponse.builder().status(BucketVersioningStatus.SUSPENDED).build());
        assertThat(guard(false, UNIQUE_SECRET, HTTP).check().reasons()).containsExactly("versioning_suspended:" + BUCKET, "versioning_suspended:" + CLUB_DOCUMENTS);
    }

    @Test
    void strictRefusesTheOldAndTheNewDevSecretAndPlainHttp() {
        assertThat(guard(true, "SuperSecretKey123", HTTPS).check().reasons()).containsExactly("dev_secret");
        assertThat(guard(true, "dev-only-medical-app-secret", HTTPS).check().reasons()).containsExactly("dev_secret");
        assertThat(guard(true, UNIQUE_SECRET, HTTP).check().reasons()).containsExactly("public_endpoint_not_https");
    }

    @Test
    void withoutStrictTheDevSecretAndHttpAreFine() {
        assertThat(guard(false, "SuperSecretKey123", HTTP).check().secure()).isTrue();
        assertThat(guard(false, "dev-only-medical-app-secret", HTTP).check().secure()).isTrue();
    }

    @Test
    void anUnreachableStorageIsNotSecure() {
        when(s3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenThrow(SdkClientException.create("connection refused"));

        assertThat(guard(false, UNIQUE_SECRET, HTTP).check().reasons()).containsExactly("unreachable:" + BUCKET, "unreachable:" + CLUB_DOCUMENTS);
    }

    @Test
    void requireSecureThrowsA503AndChecksAgainAtMostOnceAMinute() {
        when(s3Client.getBucketEncryption(any(GetBucketEncryptionRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());
        MedicalStorageGuard guard = guard(false, UNIQUE_SECRET, HTTP);
        guard.check();

        assertThatThrownBy(guard::requireSecure)
                .isInstanceOf(ExternalServiceException.class)
                .satisfies(ex -> assertThat(((ExternalServiceException) ex).getMessageCode())
                        .isEqualTo("error.medical.storage_not_secure"));
        verify(s3Client, times(2)).getBucketEncryption(any(GetBucketEncryptionRequest.class));

        // The setup script ran meanwhile: after a minute the next request sees it.
        encrypted();
        clock.advance(MedicalStorageGuard.RECHECK_AFTER.plusSeconds(1));
        guard.requireSecure();
        assertThat(guard.isSecure()).isTrue();
        verify(s3Client, times(4)).getBucketEncryption(any(GetBucketEncryptionRequest.class));
    }

    @Test
    void theClubDocumentsBucketIsOnTheListToo() {
        doThrow(S3Exception.builder().statusCode(404).build())
                .when(s3Client).getBucketEncryption(argThat((GetBucketEncryptionRequest request) -> CLUB_DOCUMENTS.equals(request.bucket())));

        var result = guard(false, UNIQUE_SECRET, HTTP).check();

        assertThat(result.secure()).isFalse();
        assertThat(result.reasons()).containsExactly("encryption_missing:" + CLUB_DOCUMENTS);
    }

    @Test
    void nothingIsSecureBeforeTheFirstCheck() {
        assertThat(guard(false, UNIQUE_SECRET, HTTP).isSecure()).isFalse();
    }

    /** {@code doReturn}, because a {@code when(...)} on a stub that throws would throw while re-stubbing. */
    private void encrypted() {
        doReturn(GetBucketEncryptionResponse.builder()
                        .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder()
                                .rules(ServerSideEncryptionRule.builder()
                                        .applyServerSideEncryptionByDefault(ServerSideEncryptionByDefault.builder()
                                                .sseAlgorithm(ServerSideEncryption.AES256)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .when(s3Client).getBucketEncryption(any(GetBucketEncryptionRequest.class));
    }

    private MedicalStorageGuard guard(boolean strict, String secret, String publicEndpoint) {
        return new MedicalStorageGuard(s3Client, BUCKET, CLUB_DOCUMENTS, secret, publicEndpoint, strict, clock);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
