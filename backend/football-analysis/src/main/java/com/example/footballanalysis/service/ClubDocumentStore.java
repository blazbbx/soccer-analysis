package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ExternalServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Duration;
import java.util.UUID;

/**
 * The private {@code club-documents} bucket: the clubs' privacy notices as PDF. Not health data, so the file goes
 * through the application, but the bucket is private, encrypted like {@code medical-docs}, and the key holds only
 * ids ({@code notices/{clubId}/{uuid}.pdf}), never a user file name.
 */
@Service
@Slf4j
public class ClubDocumentStore {

    public static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(10);

    private final S3Client s3Client;
    private final S3PresignerService presignerService;
    private final String bucket;

    public ClubDocumentStore(S3Client s3Client,
                             S3PresignerService presignerService,
                             @Value("${minio.buckets.club-documents:club-documents}") String bucket) {
        this.s3Client = s3Client;
        this.presignerService = presignerService;
        this.bucket = bucket;
    }

    /** Like {@link MedicalFileStore#ensureBucket}: an existing dev MinIO predates the bucket. */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void ensureBucket() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException ex) {
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("Created private bucket {}", bucket);
            } catch (RuntimeException createEx) {
                log.warn("Could not create bucket {}", bucket, createEx);
            }
        } catch (RuntimeException ex) {
            log.warn("Could not check bucket {}: {}", bucket, ex.getMessage());
        }
    }

    public String bucket() {
        return bucket;
    }

    public static String noticeKey(UUID clubId) {
        return "notices/" + clubId + "/" + UUID.randomUUID() + ".pdf";
    }

    /**
     * Stores the PDF now and removes it again if the surrounding transaction rolls back, so a failed save does not
     * leave an object no club row points to.
     */
    public void putPdf(String key, byte[] bytes) {
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType("application/pdf")
                            .contentLength((long) bytes.length)
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (RuntimeException ex) {
            log.warn("Could not store a club document: {}", ex.getMessage());
            throw new ExternalServiceException("error.medical.storage_unavailable", new Object[0],
                    "The file storage is not reachable right now. Try again in a moment.", ex);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        delete(key);
                    }
                }
            });
        }
    }

    /** {@code inline} and {@code no-store, private}, like the certificate's own download. */
    public String downloadUrl(String key) {
        return presignerService.generateDownloadUrl(bucket, key, DOWNLOAD_URL_TTL, "application/pdf",
                "inline; filename=\"adatkezelesi-tajekoztato.pdf\"", MedicalFileStore.DOWNLOAD_CACHE_CONTROL);
    }

    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException ex) {
            log.warn("Could not delete a club document, the object stays orphaned");
        }
    }

    /** Deletes the object after the current transaction commits. A rollback keeps it. */
    public void deleteAfterCommit(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete(key);
                }
            });
            return;
        }
        delete(key);
    }
}
