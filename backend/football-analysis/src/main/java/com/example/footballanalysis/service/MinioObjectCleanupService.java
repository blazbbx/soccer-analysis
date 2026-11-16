package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.StorageDeletion;
import com.example.footballanalysis.repository.StorageDeletionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Deletes the MinIO files of matches and clips: raw video, HLS stream, tracking JSON, field detection image and
 * clip assets.
 *
 * <p>The files go only after the database delete has committed. Inside a transaction every object (or prefix) is
 * first written to {@code storage_deletions} in that same transaction, and deleted after the commit. So a delete
 * that rolls back leaves every file in place (no match row without its video), and a file that cannot be deleted
 * stays queued for {@link #retryQueuedDeletions()} instead of becoming an orphan. Deleting twice is harmless.
 * Outside a transaction the files are deleted at once, and only the failures are queued.
 */
@Service
@Slf4j
public class MinioObjectCleanupService {

    private final S3Client s3Client;
    private final String rawVideoBucket;
    private final String trackingDataBucket;
    private final String hlsBucket;
    private final String clipsBucket;
    private final String fieldDetectionBucket;
    // Null only in unit tests built without a database: then every delete runs at once.
    private final StorageDeletionRepository deletionRepository;
    private final TransactionTemplate requiresNew;
    private final Clock clock;

    @Autowired
    public MinioObjectCleanupService(
            S3Client s3Client,
            @Value("${minio.buckets.raw-videos}") String rawVideoBucket,
            @Value("${minio.buckets.tracking-data}") String trackingDataBucket,
            @Value("${minio.buckets.hls-streams:hls-streams}") String hlsBucket,
            @Value("${minio.buckets.clips}") String clipsBucket,
            @Value("${minio.buckets.field-detection:field-detection}") String fieldDetectionBucket,
            StorageDeletionRepository deletionRepository,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.s3Client = s3Client;
        this.rawVideoBucket = rawVideoBucket;
        this.trackingDataBucket = trackingDataBucket;
        this.hlsBucket = hlsBucket;
        this.clipsBucket = clipsBucket;
        this.fieldDetectionBucket = fieldDetectionBucket;
        this.deletionRepository = deletionRepository;
        this.clock = clock;
        if (transactionManager != null) {
            this.requiresNew = new TransactionTemplate(transactionManager);
            this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        } else {
            this.requiresNew = null;
        }
    }

    /** Without a database: deletes at once and queues nothing (unit tests). */
    MinioObjectCleanupService(S3Client s3Client, String rawVideoBucket, String trackingDataBucket, String hlsBucket,
                              String clipsBucket, String fieldDetectionBucket) {
        this(s3Client, rawVideoBucket, trackingDataBucket, hlsBucket, clipsBucket, fieldDetectionBucket,
                null, null, Clock.systemUTC());
    }

    /** A bucket and a key, or every object under a key prefix. */
    record Target(String bucket, String key, boolean prefix) {
        static Target object(String bucket, String key) {
            return new Target(bucket, key, false);
        }

        static Target under(String bucket, String prefix) {
            return new Target(bucket, prefix, true);
        }
    }

    // Csapat törlésekor az összes hozzá tartozó meccs és clip MinIO objektumát is eltakarítja.
    public void deleteMatchArtifactsForTeamDeletion(Collection<Match> matches, Collection<Clip> clips) {
        List<Target> targets = new ArrayList<>();
        if (matches != null) {
            matches.forEach(match -> addMatchTargets(targets, match));
        }
        addClipTargets(targets, clips);
        log.atInfo()
                .setMessage("MinIO cleanup scheduled for team deletion")
                .addKeyValue("cleanup_type", "TEAM_DELETION")
                .addKeyValue("deleted_matches_count", matches != null ? matches.size() : 0)
                .addKeyValue("deleted_clips_count", clips != null ? clips.size() : 0)
                .log();
        schedule(targets);
    }

    // Egyetlen meccs összes MinIO artefaktját törli, a clip fájlokkal együtt.
    public void deleteMatchArtifacts(Match match, Collection<Clip> clips) {
        if (match == null) {
            return;
        }
        List<Target> targets = new ArrayList<>();
        addMatchTargets(targets, match);
        addClipTargets(targets, clips);
        log.atInfo()
                .setMessage("MinIO cleanup scheduled for match and its clips")
                .addKeyValue("match_id", match.getId())
                .addKeyValue("clips_count", clips != null ? clips.size() : 0)
                .log();
        schedule(targets);
    }

    // A meccshez tartozó raw videót, tracking JSON-t, HLS csomagot és pályaképet törli.
    public void deleteMatchArtifacts(Match match) {
        deleteMatchArtifacts(match, List.of());
    }

    // Egy clip mappáját törli.
    public void deleteClipArtifact(Clip clip) {
        List<Target> targets = new ArrayList<>();
        addClipTargets(targets, clip == null ? List.of() : List.of(clip));
        schedule(targets);
    }

    /**
     * Retries the deletions still queued (oldest first, a batch at a time).
     *
     * @return how many were finished
     */
    public int retryQueuedDeletions() {
        if (deletionRepository == null) {
            return 0;
        }
        List<StorageDeletion> queued = deletionRepository.findTop500ByOrderByCreatedAtAsc();
        int done = deleteQueued(queued);
        if (done < queued.size()) {
            log.warn("{} MinIO deletions failed again; they stay queued", queued.size() - done);
        }
        return done;
    }

    private void addMatchTargets(List<Target> targets, Match match) {
        if (match == null || match.getId() == null) {
            return;
        }
        UUID matchId = match.getId();
        if (match.getSavedMinioFileName() != null && !match.getSavedMinioFileName().isBlank()) {
            targets.add(Target.object(rawVideoBucket, match.getSavedMinioFileName()));
        }
        // The stored URLs came from worker messages: only their key is used, and only in the expected bucket.
        String trackingKey = keyInBucket(match.getTrackingDataUrl(), trackingDataBucket);
        targets.add(Target.object(trackingDataBucket, trackingKey != null ? trackingKey : matchId + ".json"));
        targets.add(Target.under(hlsBucket, matchId + "/"));
        String imageKey = keyInBucket(match.getDefishedImageUrl(), fieldDetectionBucket);
        targets.add(Target.object(fieldDetectionBucket, imageKey != null ? imageKey : matchId + ".jpg"));
    }

    private void addClipTargets(List<Target> targets, Collection<Clip> clips) {
        if (clips == null) {
            return;
        }
        for (Clip clip : clips) {
            String prefix = buildClipPrefix(clip);
            if (prefix != null) {
                targets.add(Target.under(clipsBucket, prefix));
            }
        }
    }

    private String buildClipPrefix(Clip clip) {
        if (clip == null || clip.getMatch() == null || clip.getMatch().getId() == null || clip.getId() == null) {
            return null;
        }
        return clip.getMatch().getId() + "/" + clip.getId() + "/";
    }

    private void schedule(List<Target> targets) {
        if (targets.isEmpty()) {
            return;
        }
        if (deletionRepository == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteNow(targets);
            return;
        }
        // Same transaction as the database delete: a rollback drops these rows too, and nothing is deleted.
        List<StorageDeletion> queued = deletionRepository.saveAll(targets.stream().map(this::newRow).toList());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // The commit already happened: nothing may escape into the caller; the retry job finishes the rest.
                try {
                    deleteQueued(queued);
                } catch (RuntimeException ex) {
                    log.warn("Could not finish the MinIO deletions after commit; the retry job will", ex);
                }
            }
        });
    }

    private void deleteNow(List<Target> targets) {
        List<Target> failed = targets.stream().filter(target -> !delete(target)).toList();
        if (!failed.isEmpty() && deletionRepository != null) {
            requiresNew.executeWithoutResult(status -> deletionRepository.saveAll(failed.stream().map(this::newRow).toList()));
        }
    }

    /** Own transaction: an after-commit callback still sits on the finished one, which would not write anything. */
    private int deleteQueued(List<StorageDeletion> queued) {
        List<UUID> gone = queued.stream()
                .filter(row -> delete(new Target(row.getBucket(), row.getObjectKey(), row.isPrefix())))
                .map(StorageDeletion::getId)
                .toList();
        if (!gone.isEmpty()) {
            requiresNew.executeWithoutResult(status -> deletionRepository.deleteAllByIdInBatch(gone));
        }
        return gone.size();
    }

    /** @return true when the object (or every object under the prefix) is gone */
    private boolean delete(Target target) {
        try {
            if (target.prefix()) {
                deleteObjectsByPrefix(target.bucket(), target.key());
            } else {
                s3Client.deleteObject(DeleteObjectRequest.builder().bucket(target.bucket()).key(target.key()).build());
            }
            log.debug("Deleted MinIO {} {}/{}", target.prefix() ? "prefix" : "object", target.bucket(), target.key());
            return true;
        } catch (RuntimeException ex) {
            log.atWarn()
                    .setCause(ex)
                    .setMessage("Failed to delete from MinIO; queued for retry")
                    .addKeyValue("bucket", target.bucket())
                    .addKeyValue("key", target.key())
                    .addKeyValue("prefix", target.prefix())
                    .log();
            return false;
        }
    }

    // Prefix alapján listázza és kötegekben törli az összes érintett objektumot.
    private void deleteObjectsByPrefix(String bucket, String prefix) {
        List<ObjectIdentifier> identifiers = new ArrayList<>();
        for (var page : s3Client.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build())) {
            for (S3Object object : page.contents()) {
                identifiers.add(ObjectIdentifier.builder().key(object.key()).build());
            }
        }
        for (int index = 0; index < identifiers.size(); index += 1000) {
            List<ObjectIdentifier> batch = identifiers.subList(index, Math.min(index + 1000, identifiers.size()));
            var response = s3Client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(batch).quiet(true).build())
                    .build());
            if (response != null && response.hasErrors() && !response.errors().isEmpty()) {
                throw new IllegalStateException(response.errors().size() + " objects under " + bucket + "/" + prefix
                        + " could not be deleted");
            }
        }
    }

    private StorageDeletion newRow(Target target) {
        StorageDeletion row = new StorageDeletion();
        row.setId(UUID.randomUUID());
        row.setBucket(target.bucket());
        row.setObjectKey(target.key());
        row.setPrefix(target.prefix());
        row.setCreatedAt(clock.instant());
        return row;
    }

    // MinIO URL-ből kinyeri a key részt, de csak a várt bucketen belül: az URL workerüzenetből jön,
    // egy hamis üzenet így sem törölhet másik bucketből (pl. a sportorvosiból).
    private static String keyInBucket(String objectUrl, String expectedBucket) {
        if (objectUrl == null || objectUrl.isBlank()) {
            return null;
        }
        try {
            String path = URI.create(objectUrl).getPath();
            String prefix = "/" + expectedBucket + "/";
            if (path == null || !path.startsWith(prefix) || path.length() == prefix.length() || path.contains("..")) {
                log.debug("Ignoring object URL outside the {} bucket: {}", expectedBucket, objectUrl);
                return null;
            }
            return path.substring(prefix.length());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
