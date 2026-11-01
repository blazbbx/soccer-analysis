package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ExternalServiceException;
import com.example.footballanalysis.model.db.MedicalFileDeletion;
import com.example.footballanalysis.repository.MedicalFileDeletionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The private {@code medical-docs} bucket. Keys hold only ids ({@code medical/{profileId}/{certificateId}}),
 * never a user file name. URLs are never logged.
 */
@Service
@Slf4j
public class MedicalFileStore {

    public static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(10);
    public static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(2);
    public static final String DOWNLOAD_CACHE_CONTROL = "no-store, private";

    private final S3Client s3Client;
    private final S3PresignerService presignerService;
    private final String bucket;
    private final MedicalFileDeletionRepository deletionRepository;
    private final Clock clock;
    private final TransactionTemplate requiresNew;

    public MedicalFileStore(S3Client s3Client,
                            S3PresignerService presignerService,
                            @Value("${minio.buckets.medical-docs:medical-docs}") String bucket,
                            MedicalFileDeletionRepository deletionRepository,
                            Clock clock,
                            PlatformTransactionManager transactionManager) {
        this.s3Client = s3Client;
        this.presignerService = presignerService;
        this.bucket = bucket;
        this.deletionRepository = deletionRepository;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * The setup scripts create the bucket on a fresh MinIO. An existing dev MinIO predates it, so the bucket
     * is created here when missing. A new bucket is private: no anonymous policy, no webhook. It has no
     * encryption either, so {@link MedicalStorageGuard}, which runs after this, refuses new files until the
     * setup script is run again.
     */
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

    public static String objectKey(UUID profileId, UUID certificateId) {
        return "medical/" + profileId + "/" + certificateId;
    }

    public String uploadUrl(String key, String contentType, long sizeBytes) {
        return presignerService.generateUploadUrl(bucket, key, contentType, sizeBytes, UPLOAD_URL_TTL);
    }

    /** {@code no-store, private}: the browser must not keep the opened certificate in its cache. */
    public String downloadUrl(String key, String contentType) {
        String disposition = "inline; filename=\"sportorvosi." + extension(contentType) + "\"";
        return presignerService.generateDownloadUrl(bucket, key, DOWNLOAD_URL_TTL, contentType, disposition, DOWNLOAD_CACHE_CONTROL);
    }

    /**
     * Size of the stored object, or empty when MinIO says it is missing (404). Any other failure throws
     * {@link ExternalServiceException}: an outage must not be taken for a missing file.
     */
    public Optional<Long> size(String key) {
        try {
            return Optional.ofNullable(s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())
                    .contentLength());
        } catch (RuntimeException ex) {
            if (isNotFound(ex)) {
                return Optional.empty();
            }
            log.warn("Could not read medical object size: {}", ex.getMessage());
            throw unavailable(ex);
        }
    }

    /**
     * The first bytes of the object, with a range read. Empty when MinIO says it is missing (404); any other
     * failure throws {@link ExternalServiceException}.
     */
    public Optional<byte[]> firstBytes(String key, int count) {
        try {
            ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .range("bytes=0-" + (count - 1))
                    .build());
            return Optional.of(bytes.asByteArray());
        } catch (RuntimeException ex) {
            if (isNotFound(ex)) {
                return Optional.empty();
            }
            log.warn("Could not read the first bytes of a medical object: {}", ex.getMessage());
            throw unavailable(ex);
        }
    }

    private static boolean isNotFound(RuntimeException ex) {
        return ex instanceof NoSuchKeyException || (ex instanceof S3Exception s3 && s3.statusCode() == 404);
    }

    private static ExternalServiceException unavailable(RuntimeException cause) {
        return new ExternalServiceException("error.medical.storage_unavailable", new Object[0],
                "The file storage is not reachable right now. Try again in a moment.", cause);
    }

    /**
     * Deletes the object now, outside a transaction. A failure queues it for the nightly retry. Logs carry the
     * certificate id only, never the key.
     */
    public void delete(String key, UUID certificateId) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (!deleteObject(key, certificateId)) {
            try {
                requiresNew.executeWithoutResult(status -> deletionRepository.save(newDeletion(key, certificateId)));
            } catch (RuntimeException ex) {
                log.error("Could not queue the failed delete of the medical file of certificate {}, the object stays orphaned", certificateId, ex);
            }
        }
    }

    /**
     * Queues the objects in the current transaction and deletes them after it commits. A rollback keeps the objects and
     * drops the queue rows with it. A failed delete leaves its row for {@link #retryQueuedDeletions}.
     */
    public void deleteAfterCommit(Collection<KeyRef> refs) {
        List<KeyRef> keys = refs == null ? List.of() : refs.stream()
                .filter(Objects::nonNull)
                .filter(ref -> ref.key() != null && !ref.key().isBlank())
                .toList();
        if (keys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            keys.forEach(ref -> delete(ref.key(), ref.certificateId()));
            return;
        }
        List<MedicalFileDeletion> queued = deletionRepository.saveAll(keys.stream()
                .map(ref -> newDeletion(ref.key(), ref.certificateId()))
                .toList());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // The commit already happened: nothing may escape into the caller, the nightly retry finishes the rest.
                try {
                    deleteQueued(queued);
                } catch (RuntimeException ex) {
                    log.warn("Could not finish the medical file deletions after commit, the nightly cleanup retries them", ex);
                }
            }
        });
    }

    public void deleteAfterCommit(String key, UUID certificateId) {
        deleteAfterCommit(List.of(new KeyRef(key, certificateId)));
    }

    /** The nightly retry of the queued deletions. Deleting an object twice is harmless, so a crash in between is safe. */
    public int retryQueuedDeletions() {
        List<MedicalFileDeletion> queued = deletionRepository.findAll();
        int deleted = deleteQueued(queued);
        if (deleted < queued.size()) {
            log.warn("{} medical files could not be deleted, retrying at the next cleanup", queued.size() - deleted);
        }
        return deleted;
    }

    /** Own transaction: an after-commit callback still sits on the finished one, which would not write anything. */
    private int deleteQueued(List<MedicalFileDeletion> queued) {
        List<MedicalFileDeletion> gone = queued.stream()
                .filter(row -> deleteObject(row.getObjectKey(), row.getCertificateId()))
                .toList();
        if (!gone.isEmpty()) {
            requiresNew.executeWithoutResult(status -> deletionRepository.deleteAllByIdInBatch(
                    gone.stream().map(MedicalFileDeletion::getId).toList()));
        }
        return gone.size();
    }

    private boolean deleteObject(String key, UUID certificateId) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (RuntimeException ex) {
            log.warn("Could not delete medical file of certificate {}", certificateId);
            return false;
        }
    }

    private MedicalFileDeletion newDeletion(String key, UUID certificateId) {
        MedicalFileDeletion row = new MedicalFileDeletion();
        row.setId(UUID.randomUUID());
        row.setObjectKey(key);
        row.setCertificateId(certificateId);
        row.setCreatedAt(clock.instant());
        return row;
    }

    private static String extension(String contentType) {
        if ("application/pdf".equals(contentType)) {
            return "pdf";
        }
        if ("image/png".equals(contentType)) {
            return "png";
        }
        return "jpg";
    }

    public record KeyRef(String key, UUID certificateId) {}
}
