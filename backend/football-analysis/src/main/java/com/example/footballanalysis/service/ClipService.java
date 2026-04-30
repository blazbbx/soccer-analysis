package com.example.footballanalysis.service;

import com.example.footballanalysis.config.RabbitMQConfig;
import com.example.footballanalysis.dto.ClipRenderStartMessage;
import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ExternalServiceException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.clip.ClipSyncEvent;
import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.ClipCreateRequest;
import com.example.footballanalysis.model.requests.ClipCreateWithUploadRequest;
import com.example.footballanalysis.model.requests.ClipCompositionUploadRequest;
import com.example.footballanalysis.model.requests.ClipUpdateRequest;
import com.example.footballanalysis.model.responses.ClipCompositionUploadResponse;
import com.example.footballanalysis.model.responses.ClipResponse;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

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
    private final RabbitTemplate rabbitTemplate;
    private final UserAccessService userAccessService;

    @Value("${minio.buckets.clips}")
    private String clipsBucket;

    @Value("${minio.buckets.raw-videos}")
    private String rawVideosBucket;

    private static final DateTimeFormatter MATCH_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String RENDER_STATUS_PENDING_UPLOAD = "PENDING_UPLOAD";
    private static final String RENDER_STATUS_QUEUED = "QUEUED";
    private static final String RENDER_STATUS_PROCESSING = "PROCESSING";
    private static final String RENDER_STATUS_COMPLETED = "COMPLETED";
    private static final String RENDER_STATUS_FAILED = "FAILED";

    @Transactional
    public ClipResponse createClip(UUID matchId, ClipCreateRequest request, Jwt jwt) {
        log.debug("Creating clip metadata for match ID: {}", matchId);

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new NotFoundException("error.match.not_found", new Object[]{matchId}, "Match not found: " + matchId));
        User actor = resolveCurrentUser(jwt);

        //ensureCanAccessMatch(actor, match);
        userAccessService.canAccessMatch(actor, match.getId());

        validateCreateRequest(request);

        UUID clipId = UUID.randomUUID();
        String clipDisplayName = buildClipDisplayName(match, request.name());
        User createdBy = actor;

        Clip clip = new Clip();
        clip.setId(clipId);
        clip.setMatch(match);
        clip.setCreatedBy(createdBy);
        clip.setName(clipDisplayName);
        clip.setSyncData(normalizeSyncData(request.syncData()));
        clip.setRenderStatus(RENDER_STATUS_PENDING_UPLOAD);

        clipRepository.save(clip);

        log.info("Clip {} created for match {} by user {} ({})", clipId, matchId, createdBy.getId(), createdBy.getUserRole());

        return toResponse(clip);
    }

    @Transactional
    public ClipCompositionUploadResponse createClipWithUploadLinks(UUID matchId, ClipCreateWithUploadRequest request, Jwt jwt) {
        ClipCreateRequest createRequest = new ClipCreateRequest(
            matchId,
            request.name(),
            request.syncData());

        ClipResponse clip = createClip(matchId, createRequest, jwt);
        ClipCompositionUploadRequest uploadRequest = new ClipCompositionUploadRequest(
                request.overlayFilename(),
                request.audioFilename(),
                request.timelineFilename());
        return initiateCompositionUpload(clip.id(), uploadRequest, jwt);
    }

    @Transactional
    public ClipCompositionUploadResponse initiateCompositionUpload(UUID clipId, ClipCompositionUploadRequest request, Jwt jwt) {
        log.debug("Initiating composition upload for clip {}", clipId);

        Clip clip = clipRepository.findById(clipId)
            .orElseThrow(() -> new NotFoundException("error.clip.not_found", new Object[]{clipId}, "Clip not found: " + clipId));

        Match match = clip.getMatch();
        UUID matchId = match != null ? match.getId() : null;
        if (matchId == null) {
            throw new NotFoundException("error.match.not_found", new Object[]{clipId}, "Match not found for clip: " + clipId);
        }

        userAccessService.canAccessClip(resolveCurrentUser(jwt), clip.getId());

        clip.setRenderStatus(RENDER_STATUS_PENDING_UPLOAD);
        clip.setRenderError(null);
        clip.setRenderedAt(null);
        clipRepository.save(clip);

        String overlayKey = buildOverlayObjectKey(matchId, clipId);
        String audioKey = buildAudioObjectKey(matchId, clipId);
        String timelineKey = buildTimelineObjectKey(matchId, clipId);

        return new ClipCompositionUploadResponse(
                clip.getId(),
                videoStorageService.generateClipUploadUrl(overlayKey, "video/webm"),
                videoStorageService.generateClipUploadUrl(audioKey, "audio/mpeg"),
                videoStorageService.generateClipUploadUrl(timelineKey, "application/json")
        );
    }

    @Transactional
    public ClipResponse completeCompositionUpload(UUID clipId, Jwt jwt) {
        log.debug("Completing composition upload for clip {}", clipId);

        Clip clip = clipRepository.findById(clipId)
            .orElseThrow(() -> new NotFoundException("error.clip.not_found", new Object[]{clipId}, "Clip not found: " + clipId));

        userAccessService.canAccessClip(resolveCurrentUser(jwt), clip.getId());

        validateCompositionAssets(clip);

        clip.setRenderStatus(RENDER_STATUS_QUEUED);
        clip.setRenderError(null);
        clip.setRenderedAt(null);
        clipRepository.save(clip);

        publishRenderStartMessage(clip);

        return toResponse(clip);
    }

        @Transactional
            public void markRenderProcessing(UUID clipId) {
        Clip clip = clipRepository.findById(clipId)
            .orElseThrow(() -> new NotFoundException("error.clip.not_found", new Object[]{clipId}, "Clip not found: " + clipId));

        clip.setRenderStatus(RENDER_STATUS_PROCESSING);
        clip.setRenderError(null);
        clipRepository.save(clip);
        }

        @Transactional
        public void markRenderCompleted(UUID clipId) {
        Clip clip = clipRepository.findById(clipId)
            .orElseThrow(() -> new NotFoundException("error.clip.not_found", new Object[]{clipId}, "Clip not found: " + clipId));

        clip.setRenderStatus(RENDER_STATUS_COMPLETED);
        clip.setRenderError(null);
        clip.setRenderedAt(LocalDateTime.now());
        clipRepository.save(clip);

        User createdBy = clip.getCreatedBy();
        if (createdBy != null) {
            String matchIdStr = clip.getMatch() != null ? String.valueOf(clip.getMatch().getId()) : "null";
            auditEventService.record(
                "CLIP_CREATED",
                createdBy.getId(),
                createdBy.getUserRole(),
                "CLIP",
                String.valueOf(clipId),
                "matchId=" + matchIdStr + ", name=" + clip.getName()
            );
        }
        }

        @Transactional
        public void markRenderFailed(UUID clipId, String errorMessage) {
        Clip clip = clipRepository.findById(clipId)
            .orElseThrow(() -> new NotFoundException("error.clip.not_found", new Object[]{clipId}, "Clip not found: " + clipId));

        clip.setRenderStatus(RENDER_STATUS_FAILED);
        clip.setRenderError(errorMessage);
        clipRepository.save(clip);
        }

    @Transactional
    public ClipResponse updateClip(UUID clipId, ClipUpdateRequest request, Jwt jwt) {
        log.debug("Updating clip {}", clipId);

        Clip clip = clipRepository.findById(clipId)
                .orElseThrow(() -> new NotFoundException("error.clip.not_found", new Object[]{clipId}, "Clip not found: " + clipId));

        userAccessService.canAccessClip(resolveCurrentUser(jwt), clip.getId());

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

        if (request.syncData() != null && !request.syncData().equals(clip.getSyncData())) {
            clip.setSyncData(normalizeSyncData(request.syncData()));
            updated = true;
        }

        validatePersistedClip(clip);
        clipRepository.save(clip);

        if (updated) {
            User actor = resolveCurrentUser(jwt);
            UUID matchId = clip.getMatch() != null ? clip.getMatch().getId() : null;
            log.info("Clip {} updated for match {} by user {} ({})", clipId, matchId, actor.getId(), actor.getUserRole());
        }

        return toResponse(clip);
    }

    @Transactional
    public void deleteClip(UUID clipId, Jwt jwt) {
        log.debug("Deleting clip {}", clipId);

        Clip clip = clipRepository.findById(clipId)
                .orElseThrow(() -> new NotFoundException("error.clip.not_found", new Object[]{clipId}, "Clip not found: " + clipId));

        User actor = resolveCurrentUser(jwt);
        userAccessService.canAccessClip(actor, clip.getId());

        minioObjectCleanupService.deleteClipArtifact(clip);
        clipRepository.delete(clip);

        UUID matchId = clip.getMatch() != null ? clip.getMatch().getId() : null;
        log.info("Clip {} deleted for match {} by user {} ({})", clipId, matchId, actor.getId(), actor.getUserRole());
    }

    @Transactional(readOnly = true)
    public List<ClipResponse> getClipsForMatch(UUID matchId, Jwt jwt) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new NotFoundException("error.match.not_found", new Object[]{matchId}, "Match not found: " + matchId));

        //ensureCanAccessMatch(resolveCurrentUser(jwt), match);
        userAccessService.canAccessMatch(resolveCurrentUser(jwt), match.getId());

        return clipRepository.findAllByMatch_IdOrderByCreatedAtDesc(matchId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public String generateRenderedClipDownloadUrl(UUID clipId, Jwt jwt) {
        User actor = resolveCurrentUser(jwt);

        Clip clip = clipRepository.findById(clipId)
                .orElseThrow(() -> new NotFoundException("error.clip.not_found", new Object[]{clipId}, "Clip not found: " + clipId));

        userAccessService.canAccessClip(actor, clip.getId());

        return videoStorageService.generateDownloadUrl(clipsBucket, buildRenderedObjectKey(clip), Duration.ofSeconds(30));
    }

    @Transactional(readOnly = true)
    public ClipResponse getClipById(UUID clipId, Jwt jwt) {
        User actor = resolveCurrentUser(jwt);

        Clip clip = clipRepository.findById(clipId)
                .orElseThrow(() -> new NotFoundException("error.clip.not_found", new Object[]{clipId}, "Clip not found: " + clipId));

        userAccessService.canAccessClip(actor, clip.getId());

        return toResponse(clip);
    }

    private void ensureCanAccessClip(User actor, Clip clip) {
        if (actor == null || clip == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required to access this resource.");
        }

        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }

        Match match = clip.getMatch();
        if (match == null) {
            throw new NotFoundException("error.match.not_found", new Object[0], "Match not found for this clip.");
        }

        Team homeTeam = match.getHomeTeam();
        Team awayTeam = match.getAwayTeam();

        UUID homeTeamId = homeTeam != null ? homeTeam.getId() : null;
        UUID awayTeamId = awayTeam != null ? awayTeam.getId() : null;

        if (homeTeamId == null && awayTeamId == null) {
            throw new BadRequestException("validation.clip.access.denied", new Object[0], "This clip is not associated with an accessible team.");
        }

        if (actor instanceof com.example.footballanalysis.model.db.user.Admin) {
            return;
        }

        //boolean canAccess = hasTeamAccess(actor, homeTeamId, awayTeamId);
        List<UUID> teamIds = Stream.of(homeTeam, awayTeam)
                .filter(Objects::nonNull)
                .map(Team::getId)
                .filter(Objects::nonNull)
                .toList();
        boolean canAccess = userAccessService.hasTeamAccess(actor, teamIds);

        if (!canAccess) {
            throw new UnauthorizedException("error.auth.forbidden", new Object[0], "Access denied.");
        }
    }

    private void ensureCanAccessMatch(User actor, Match match) {
        if (actor == null || match == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required to access this resource.");
        }

        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }

        UUID homeTeamId = match.getHomeTeam() != null ? match.getHomeTeam().getId() : null;
        UUID awayTeamId = match.getAwayTeam() != null ? match.getAwayTeam().getId() : null;

        if (homeTeamId == null && awayTeamId == null) {
            throw new BadRequestException("validation.match.access.denied", new Object[0], "This match is not associated with an accessible team.");
        }
        List<UUID> teamIds = Stream.of(match.getHomeTeam(), match.getAwayTeam())
                .filter(Objects::nonNull)
                .map(Team::getId)
                .filter(Objects::nonNull)
                .toList();
        if (!userAccessService.hasTeamAccess(actor, teamIds)) {
            throw new UnauthorizedException("error.auth.forbidden", new Object[0], "Access denied.");
        }
    }

    private boolean hasTeamAccess(User actor, UUID homeTeamId, UUID awayTeamId) {
        if (actor instanceof com.example.footballanalysis.model.db.user.Coach coach) {
            return coach.getTeams().stream().map(Team::getId).anyMatch(teamId -> teamId.equals(homeTeamId) || teamId.equals(awayTeamId));
        }
        if (actor instanceof com.example.footballanalysis.model.db.user.Player player) {
            return player.getTeams().stream().map(Team::getId).anyMatch(teamId -> teamId.equals(homeTeamId) || teamId.equals(awayTeamId));
        }
        if (actor instanceof com.example.footballanalysis.model.db.user.Fan fan) {
            return fan.getTeams().stream().map(Team::getId).anyMatch(teamId -> teamId.equals(homeTeamId) || teamId.equals(awayTeamId));
        }
        return false;
    }

    private void validateCreateRequest(ClipCreateRequest request) {
        validateSyncData(request.syncData());
    }

    private void validatePersistedClip(Clip clip) {
        validateSyncData(clip.getSyncData());
    }

    private void validateSyncData(List<ClipSyncEvent> syncData) {
        if (syncData == null || syncData.isEmpty()) {
            return;
        }

        double previousTimelineTime = -1.0d;
        for (ClipSyncEvent event : syncData) {
            if (event == null || event.t() == null || event.type() == null || event.m() == null) {
                throw new BadRequestException("validation.clip.syncData.event.required", new Object[0], "Clip sync data contains an invalid event.");
            }
            if (event.t() < 0.0d) {
                throw new BadRequestException("validation.clip.syncData.t.min", new Object[0], "Clip sync event timeline time cannot be negative.");
            }
            if (event.m() < 0.0d) {
                throw new BadRequestException("validation.clip.syncData.m.min", new Object[0], "Clip sync event match time cannot be negative.");
            }
            if (event.t() <= previousTimelineTime) {
                throw new BadRequestException("validation.clip.syncData.t.order", new Object[0], "Clip sync event timeline time must be strictly increasing.");
            }
            previousTimelineTime = event.t();
        }
    }

    private List<ClipSyncEvent> normalizeSyncData(List<ClipSyncEvent> syncData) {
        return syncData == null ? null : List.copyOf(syncData);
    }

    private void validateCompositionAssets(Clip clip) {
        if (clip.getMatch() == null || clip.getMatch().getId() == null) {
            throw new BadRequestException("validation.clip.match.required", new Object[0], "Match is required before render start.");
        }
    }

    private void publishRenderStartMessage(Clip clip) {
        Match match = clip.getMatch();
        if (match == null || match.getSavedMinioFileName() == null || match.getSavedMinioFileName().isBlank()) {
            throw new BadRequestException("validation.clip.baseVideo.required", new Object[0], "Base match video is missing for this clip.");
        }

        ClipRenderStartMessage message = new ClipRenderStartMessage(
                String.valueOf(match.getId()),
                String.valueOf(clip.getId()),
                valueOrEmpty(rawVideosBucket),
                valueOrEmpty(match.getSavedMinioFileName()),
                valueOrEmpty(clipsBucket),
                "START-CLIP-RENDER"
        );

        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.CLIP_RENDER_ROUTING_KEY, message);
        } catch (AmqpException ex) {
            throw new ExternalServiceException("Failed to queue clip render task.", ex);
        }
    }

    private String buildOverlayObjectKey(UUID matchId, UUID clipId) {
        return buildAssetKey(matchId, clipId, "overlay.webm");
    }

    private String buildAudioObjectKey(UUID matchId, UUID clipId) {
        return buildAssetKey(matchId, clipId, "audio.mp3");
    }

    private String buildTimelineObjectKey(UUID matchId, UUID clipId) {
        return buildAssetKey(matchId, clipId, "timeline.json");
    }

    private String buildRenderedObjectKey(Clip clip) {
        Match match = clip.getMatch();
        if (match == null || match.getId() == null || clip.getId() == null) {
            return null;
        }
        return buildAssetKey(match.getId(), clip.getId(), "rendered.mp4");
    }

    private String buildAssetKey(UUID matchId, UUID clipId, String fileName) {
        return matchId + "/" + clipId + "/" + fileName;
    }

    private String valueOrEmpty(String value) {
        return value != null ? value : "";
    }

    private ClipResponse toResponse(Clip clip) {
        Match match = clip.getMatch();
        return new ClipResponse(
                clip.getId(),
                match != null ? match.getId() : null,
                match != null ? buildMatchDisplayName(match) : null,
                clip.getName(),
                clip.getSyncData(),
                clip.getRenderStatus(),
                buildStoragePath(clipsBucket, buildRenderedObjectKey(clip)),
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
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required to access this resource.");
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
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authenticated token does not contain an email.");
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

