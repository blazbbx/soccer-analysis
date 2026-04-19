package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.requests.ClipUpdateRequest;
import com.example.footballanalysis.model.requests.ClipUploadRequest;
import com.example.footballanalysis.model.responses.ClipResponse;
import com.example.footballanalysis.model.responses.ClipUploadResponse;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClipService {

    private final ClipRepository clipRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final S3PresignerService videoStorageService;
    private final MinioObjectCleanupService minioObjectCleanupService;
    private final AuditEventService auditEventService;

    @Value("${minio.buckets.clips}")
    private String clipsBucket;

    private static final DateTimeFormatter MATCH_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Transactional
    public ClipUploadResponse initiateClipUpload(UUID matchId, ClipUploadRequest request, Jwt jwt) {
        log.debug("Initiating clip upload for match ID: {}, original filename: {}", matchId, request.originalFilename());

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new NotFoundException("error.match.not_found", new Object[]{matchId}, "Match not found: " + matchId));

        validateRequest(request);

        UUID clipId = UUID.randomUUID();
        String extension = extractExtension(request.originalFilename());
        String objectKey = matchId + "/" + clipId + extension;
        String storagePath = buildStoragePath(clipsBucket, objectKey);
        String matchDisplayName = buildMatchDisplayName(match);
        String clipDisplayName = buildClipDisplayName(match, request.name());
        User createdBy = resolveCurrentUser(jwt);

        Clip clip = new Clip();
        clip.setId(clipId);
        clip.setMatch(match);
        clip.setCreatedBy(createdBy);
        clip.setName(clipDisplayName);
        clip.setStartSeconds(request.startSeconds());
        clip.setEndSeconds(request.endSeconds());
        clip.setStorageLocation(clipsBucket, objectKey);

        clipRepository.save(clip);

        auditEventService.record(
            "CLIP_CREATED",
            createdBy.getId(),
            createdBy.getUserRole(),
            "CLIP",
            clipId.toString(),
            "matchId=" + matchId + ", name=" + clipDisplayName + ", storagePath=" + storagePath
        );

        log.info("Clip {} created for match {} by user {} ({})", clipId, matchId, createdBy.getId(), createdBy.getUserRole());

        String uploadUrl = videoStorageService.generateClipUploadUrl(objectKey);
        return new ClipUploadResponse(clipId, matchId, matchDisplayName, uploadUrl, storagePath);
    }

    @Transactional
    public ClipResponse updateClip(UUID matchId, UUID clipId, ClipUpdateRequest request, Jwt jwt) {
        log.debug("Updating clip {} for match {}", clipId, matchId);

        Clip clip = clipRepository.findByIdAndMatch_Id(clipId, matchId)
                .orElseThrow(() -> new NotFoundException("error.clip.not_found", new Object[]{clipId}, "Clip not found: " + clipId));

        boolean updated = false;

        if (request.name() != null) {
            String normalizedName = request.name().trim();
            if (normalizedName.isEmpty()) {
                throw new BadRequestException("validation.clip.name.required", new Object[0], "Clip name is required.");
            }
            if (!normalizedName.equals(clip.getName())) {
                clip.setName(normalizedName);
                updated = true;
            }
        }

        if (request.startSeconds() != null && !request.startSeconds().equals(clip.getStartSeconds())) {
            clip.setStartSeconds(request.startSeconds());
            updated = true;
        }

        if (request.endSeconds() != null && !request.endSeconds().equals(clip.getEndSeconds())) {
            clip.setEndSeconds(request.endSeconds());
            updated = true;
        }

        validatePersistedClip(clip);
        clipRepository.save(clip);

        if (updated) {
            User actor = resolveCurrentUser(jwt);
            auditEventService.record(
                    "CLIP_UPDATED",
                    actor.getId(),
                    actor.getUserRole(),
                    "CLIP",
                    clipId.toString(),
                    "matchId=" + matchId + ", name=" + clip.getName() + ", start=" + clip.getStartSeconds() + ", end=" + clip.getEndSeconds()
            );
            log.info("Clip {} updated for match {} by user {} ({})", clipId, matchId, actor.getId(), actor.getUserRole());
        }

        return toResponse(clip);
    }

    @Transactional
    public void deleteClip(UUID matchId, UUID clipId, Jwt jwt) {
        log.debug("Deleting clip {} for match {}", clipId, matchId);

        Clip clip = clipRepository.findByIdAndMatch_Id(clipId, matchId)
                .orElseThrow(() -> new NotFoundException("error.clip.not_found", new Object[]{clipId}, "Clip not found: " + clipId));

        User actor = resolveCurrentUser(jwt);

        minioObjectCleanupService.deleteClipArtifact(clip);
        clipRepository.delete(clip);

        auditEventService.record(
            "CLIP_DELETED",
                actor.getId(),
                actor.getUserRole(),
                "CLIP",
                clipId.toString(),
                "matchId=" + matchId + ", name=" + clip.getName() + ", storagePath=" + buildStoragePath(clip.getBucket(), clip.getObjectKey())
        );

        log.info("Clip {} deleted for match {} by user {} ({})", clipId, matchId, actor.getId(), actor.getUserRole());
    }

    @Transactional(readOnly = true)
    public List<ClipResponse> getClipsForMatch(UUID matchId) {
        return clipRepository.findAllByMatch_IdOrderByCreatedAtDesc(matchId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void validateRequest(ClipUploadRequest request) {
        if (request.originalFilename() == null || request.originalFilename().isBlank()) {
            throw new BadRequestException("validation.clip.originalFilename.required", new Object[0], "Original filename is required.");
        }
        if (request.startSeconds() == null || request.startSeconds() < 0) {
            throw new BadRequestException("validation.clip.startSeconds.required", new Object[0], "Clip start seconds are required.");
        }
        if (request.endSeconds() == null || request.endSeconds() <= request.startSeconds()) {
            throw new BadRequestException("validation.clip.endSeconds.invalid", new Object[0], "Clip end seconds must be greater than start seconds.");
        }
    }

    private void validatePersistedClip(Clip clip) {
        if (clip.getStartSeconds() == null || clip.getStartSeconds() < 0) {
            throw new BadRequestException("validation.clip.startSeconds.required", new Object[0], "Clip start seconds are required.");
        }
        if (clip.getEndSeconds() == null || clip.getEndSeconds() <= clip.getStartSeconds()) {
            throw new BadRequestException("validation.clip.endSeconds.invalid", new Object[0], "Clip end seconds must be greater than start seconds.");
        }
    }

    private String extractExtension(String originalFilename) {
        int index = originalFilename.lastIndexOf('.');
        if (index < 0) {
            return ".mp4";
        }

        return originalFilename.substring(index);
    }

    private ClipResponse toResponse(Clip clip) {
        Match match = clip.getMatch();
        return new ClipResponse(
                clip.getId(),
                match != null ? match.getId() : null,
                match != null ? buildMatchDisplayName(match) : null,
                clip.getName(),
                clip.getStartSeconds(),
                clip.getEndSeconds(),
                buildStoragePath(clip.getBucket(), clip.getObjectKey()),
                clip.getCreatedAt()
        );
    }

    private String buildStoragePath(String bucket, String objectKey) {
        if (bucket == null || bucket.isBlank() || objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return bucket + "/" + objectKey;
    }

    private String buildMatchDisplayName(Match match) {
        if (match == null) {
            return "Unknown match";
        }

        String homeTeamName = match.getHomeTeam() != null ? match.getHomeTeam().getName() : "Home";
        String awayTeamName = match.getAwayTeam() != null ? match.getAwayTeam().getName() : "Away";
        StringBuilder builder = new StringBuilder(homeTeamName).append(" vs ").append(awayTeamName);

        if (match.getMatchDate() != null) {
            builder.append(" (").append(MATCH_DATE_FORMAT.format(match.getMatchDate())).append(")");
        }

        return builder.toString();
    }

    private String buildClipDisplayName(Match match, String clipName) {
        String normalizedName = clipName != null ? clipName.trim() : "";
        if (normalizedName.isEmpty()) {
            return buildMatchDisplayName(match);
        }

        return normalizedName;
    }

    private User resolveCurrentUser(Jwt jwt) {
        if (jwt == null) {
            throw new BadRequestException("error.auth.unauthorized", new Object[0], "Authentication is required to access this resource.");
        }

        return resolveUserBySubject(jwt.getSubject())
                .or(() -> userRepository.findByEmail(resolveEmail(jwt)))
            .orElseThrow(() -> new NotFoundException("error.user.not_found", new Object[]{resolveUserLookupValue(jwt)}, "User not found for authenticated user: " + resolveUserLookupValue(jwt)));
    }

    private Optional<User> resolveUserBySubject(String subject) {
        return resolveSubjectAsUuid(subject).flatMap(userRepository::findById);
    }

    private Optional<UUID> resolveSubjectAsUuid(String subject) {
        if (subject == null || subject.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(subject));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String resolveEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            email = jwt.getClaimAsString("preferred_username");
        }
        if (email == null || email.isBlank()) {
            throw new BadRequestException("error.auth.email_missing", new Object[0], "Authenticated token does not contain an email.");
        }
        return email;
    }

    private String resolveUserLookupValue(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject != null && !subject.isBlank()) {
            return subject;
        }
        return resolveEmail(jwt);
    }
}

