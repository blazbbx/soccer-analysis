package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ExternalServiceException;
import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Match;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class MinioObjectCleanupService {

    private final S3Client s3Client;
    private final String rawVideoBucket;
    private final String trackingDataBucket;
    private final String hlsBucket;
    private final String clipsBucket;

    public MinioObjectCleanupService(
            S3Client s3Client,
            @Value("${minio.buckets.raw-videos}") String rawVideoBucket,
            @Value("${minio.buckets.tracking-data}") String trackingDataBucket,
            @Value("${minio.buckets.hls-streams:hls-streams}") String hlsBucket,
            @Value("${minio.buckets.clips}") String clipsBucket) {
        this.s3Client = s3Client;
        this.rawVideoBucket = rawVideoBucket;
        this.trackingDataBucket = trackingDataBucket;
        this.hlsBucket = hlsBucket;
        this.clipsBucket = clipsBucket;
    }

    // Csapat törlésekor az összes hozzá tartozó meccs és clip MinIO objektumát is eltakarítja.
    public void deleteMatchArtifactsForTeamDeletion(Collection<Match> matches, Collection<Clip> clips) {
        int matchCount = (matches != null) ? matches.size() : 0;
        int clipCount = (clips != null) ? clips.size() : 0;

        log.info("Starting bulk MinIO cleanup for team deletion. Matches to process: {}, Clips to process: {}", matchCount, clipCount);
        if (matches != null) {
            for (Match match : matches) {
                deleteMatchArtifacts(match);

            }
        }
        deleteClipArtifacts(clips);
        log.atInfo()
                .setMessage("MinIO artifacts cleanup completed during team deletion")
                .addKeyValue("cleanup_type", "TEAM_DELETION")
                .addKeyValue("deleted_matches_count", matchCount)
                .addKeyValue("deleted_clips_count", clipCount)
                .log();
    }

    // Egyetlen meccs összes MinIO artefaktját törli, majd opcionálisan a clip fájlokat is.
    public void deleteMatchArtifacts(Match match, Collection<Clip> clips) {
        log.info("Starting artifact cleanup for match: {}", match.getId());
        deleteMatchArtifacts(match);
        deleteClipArtifacts(clips);
        log.atInfo()
                .setMessage("Artifacts deleted for match and its clips")
                .addKeyValue("match_id", match.getId())
                .addKeyValue("clips_count", clips != null ? clips.size() : 0)
                .log();
    }

    // A meccshez tartozó raw videót, tracking JSON-t és HLS csomagot törli.
    public void deleteMatchArtifacts(Match match) {
        if (match == null) {
            return;
        }
        log.info("Deleting artifacts for match: {}", match.getId());
        deleteRawVideo(match.getSavedMinioFileName());
        deleteTrackingData(match.getId(), match.getTrackingDataUrl());
        deleteHlsArtifacts(match.getId(), match.getHlsManifestUrl());

    }

    // A feltöltött nyers videót törli a raw bucketből.
    private void deleteRawVideo(String savedMinioFileName) {
        if (savedMinioFileName == null || savedMinioFileName.isBlank()) {
            return;
        }

        deleteObject(rawVideoBucket, savedMinioFileName);
    }

    // A tracking JSON-t törli az URL-ből kinyert objektumhelyről, vagy a fallback kulcs alapján.
    private void deleteTrackingData(UUID matchId, String trackingDataUrl) {
        if (matchId == null) {
            return;
        }

        String fallbackKey = matchId + ".json";
        ParsedObjectLocation location = parseObjectLocation(trackingDataUrl);
        if (location != null) {
            deleteObject(location.bucket(), location.key());

            return;
        }

        deleteObject(trackingDataBucket, fallbackKey);
    }

    // Az encoder által létrehozott HLS playlistet és chunkokat törli a match prefixe alapján.
    private void deleteHlsArtifacts(UUID matchId, String hlsManifestUrl) {
        if (matchId == null) {
            return;
        }

        String prefix = matchId + "/";
        ParsedObjectLocation location = parseObjectLocation(hlsManifestUrl);
        if (location != null) {
            deleteObjectsByPrefix(location.bucket(), prefix);
            return;
        }
        deleteObjectsByPrefix(hlsBucket, prefix);
    }

    // A clip entitásokhoz tartozó MinIO objektumokat törli, de egyedi hiba esetén nem áll le.
    public void deleteClipArtifact(Clip clip) {
        if (clip == null) {
            return;
        }

        String prefix = buildClipPrefix(clip);
        if (prefix == null) {
            return;
        }

        deleteObjectsByPrefix(clipsBucket, prefix);
    }

    // A clip entitásokhoz tartozó MinIO objektumokat törli, de egyedi hiba esetén nem áll le.
    private void deleteClipArtifacts(Collection<Clip> clips) {
        if (clips == null || clips.isEmpty()) {
            return;
        }
        int processedCount = 0;

        for (Clip clip : clips) {
            if (clip == null) {
                continue;
            }

            String prefix = buildClipPrefix(clip);
            if (prefix == null) {
                continue;
            }

            deleteObjectsByPrefix(clipsBucket, prefix);
            processedCount++;
            log.info("Deleted clip artifact prefix from MinIO. clipId={}, prefix={}", clip.getId(), prefix);
        }

        log.info("Clip cleanup finished. Processed {} clip folders.", processedCount);
    }

    private String buildClipPrefix(Clip clip) {
        if (clip == null || clip.getMatch() == null || clip.getMatch().getId() == null || clip.getId() == null) {
            return null;
        }

        return clip.getMatch().getId() + "/" + clip.getId() + "/";
    }

    // Egy konkrét MinIO objektum törlése bucket és key alapján.
    private void deleteObject(String bucket, String key) {
        if (bucket == null || bucket.isBlank() || key == null || key.isBlank()) {
            return;
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            log.debug("Deleted MinIO object: {}/{}", bucket, key);
        } catch (S3Exception ex) {
            throw new ExternalServiceException(
                    "Failed to delete MinIO object: " + bucket + "/" + key,
                    ex);
        }
    }

    // Prefix alapján listázza és kötegekben törli az összes érintett objektumot.
    private void deleteObjectsByPrefix(String bucket, String prefix) {
        log.debug("Listing and deleting objects with prefix {} from bucket {}", prefix, bucket);
        try {
            List<ObjectIdentifier> identifiers = new ArrayList<>();
            for (var page : s3Client.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build())) {
                for (S3Object object : page.contents()) {
                    identifiers.add(ObjectIdentifier.builder().key(object.key()).build());
                }
            }

            if (identifiers.isEmpty()) {
                log.debug("No objects found with prefix {} in bucket {}", prefix, bucket);
                return;
            }

            for (int index = 0; index < identifiers.size(); index += 1000) {
                List<ObjectIdentifier> batch = identifiers.subList(index, Math.min(index + 1000, identifiers.size()));
                s3Client.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(bucket)
                        .delete(Delete.builder().objects(batch).quiet(true).build())
                        .build());
            }
            log.debug("Successfully bulk deleted {} objects with prefix {} from bucket {}", identifiers.size(), prefix, bucket);
        } catch (S3Exception ex) {
            log.atError()
                    .setCause(ex)
                    .setMessage("Failed to perform bulk delete in MinIO")
                    .addKeyValue("bucket", bucket)
                    .addKeyValue("prefix", prefix)
                    .log();
        }
    }

    // MinIO URL-ből kinyeri a bucket és key részt, ha a formátum ezt lehetővé teszi.
    private ParsedObjectLocation parseObjectLocation(String objectUrl) {
        if (objectUrl == null || objectUrl.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(objectUrl);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return null;
            }

            String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
            int separatorIndex = normalizedPath.indexOf('/');
            if (separatorIndex <= 0 || separatorIndex >= normalizedPath.length() - 1) {
                return null;
            }

            String bucket = normalizedPath.substring(0, separatorIndex);
            String key = normalizedPath.substring(separatorIndex + 1);
            return new ParsedObjectLocation(bucket, key);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private record ParsedObjectLocation(String bucket, String key) {}
}