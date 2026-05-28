package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ExternalServiceException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.Corner;
import com.example.footballanalysis.model.requests.ConfirmCornersRequest;
import com.example.footballanalysis.model.requests.UpdateTrackingLabelDataRequest;
import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.requests.UpdateMatchRequest;
import com.example.footballanalysis.model.requests.UploadMatchRequest;
import com.example.footballanalysis.model.responses.MatchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.example.footballanalysis.repository.*;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.user.Fan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchService {

    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;
    private final ClipRepository clipRepository;
    private final MatchSquadMemberRepository matchSquadMemberRepository;
    private final S3PresignerService videoStorageService;
    private final MinioObjectCleanupService minioObjectCleanupService;
    private final AuditEventService auditEventService;
    @Autowired(required = false)
    private UserAccessService userAccessService;
    private final ObjectMapper objectMapper;
    private final S3Client s3Client;
    private final FanRepository fanRepository;
    private final CoachRepository coachRepository;
    private final PlayerRepository playerRepository;

    @org.springframework.beans.factory.annotation.Value("${minio.buckets.tracking-data}")
    private String trackingDataBucket;

    @Transactional
    public Map<String, String> initiateMatchUpload(UploadMatchRequest request, User actor) {
        log.debug("Initiating match upload for file: {}", request.originalFilename());

        // Kötelező mező validáció
        if (request.originalFilename() == null || request.originalFilename().isBlank()) {
            throw new BadRequestException("validation.match.originalFilename.required", new Object[0], "Original filename is required.");
        }

        String originalFilename = request.originalFilename().trim();
        String homeTeamColor = normalizeRequiredColor(request.homeTeamColor());
        String awayTeamColor = normalizeRequiredColor(request.awayTeamColor());
        String refereeColor = normalizeRequiredColor(request.refereeColor());
        String awayTeamName = normalizeOptionalTeamName(request.awayTeamName());

        // Ha van csapat ID, betöltjük – ha nincs (null), null marad
        Team homeTeam = request.homeTeamId() != null
                ? teamRepository.findById(request.homeTeamId())
                    .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{request.homeTeamId()}, "Team not found: " + request.homeTeamId()))
                : null;
        Team awayTeam = request.awayTeamId() != null
                ? teamRepository.findById(request.awayTeamId())
                    .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{request.awayTeamId()}, "Team not found: " + request.awayTeamId()))
                : null;

        //ensureCanAccessTeams(resolveCurrentUser(jwt), homeTeam, awayTeam);
        List<UUID> teamIds = Stream.of(homeTeam, awayTeam)
                .filter(Objects::nonNull)
                .map(Team::getId)
                .filter(Objects::nonNull)
                .toList();
        if (userAccessService != null) {
            userAccessService.hasTeamAccess(actor, teamIds);
        }


        Match match = new Match();
        match.setId(UUID.randomUUID());
        match.setHomeTeam(homeTeam);
        match.setAwayTeam(awayTeam);
        match.setAwayTeamName(awayTeam != null ? awayTeam.getName() : awayTeamName);

        // Meccs metaadatok (opcionális)
        match.setMatchDate(request.matchDate());
        match.setHomeTeamColor(homeTeamColor);
        match.setAwayTeamColor(awayTeamColor);
        match.setRefereeColor(refereeColor);


        String extension = "";
        int i = originalFilename.lastIndexOf('.');
        if (i > 0) extension = originalFilename.substring(i);
        String safeMinioName = match.getId() + extension;

        match.setOriginalFileName(originalFilename);
        match.setSavedMinioFileName(safeMinioName);
        match.setOverallStatus("UPLOADING");
        match.setMlStatus("PENDING");
        match.setEncodingStatus("PENDING");
        match.setFieldDetectionStatus("PENDING");

        // 4. Save to DB
        matchRepository.save(match);
        log.debug("Match record saved to DB with ID: {} and MINIO filename: {}", match.getId(), safeMinioName);

        // 5. Generate the Presigned URL using our ultra-safe filename
        String presignedUrl = videoStorageService.generateUploadUrl(safeMinioName);
        log.debug("Presigned URL generated successfully for match ID: {}", match.getId());

        // actor provided by controller
        auditEventService.record(
            "MATCH_CREATED",
            actor != null ? actor.getId() : null,
            actor != null ? actor.getUserRole() : null,
            "MATCH",
            match.getId().toString(),
            "homeTeamId=" + (homeTeam != null ? homeTeam.getId() : null) + ", awayTeamId=" + (awayTeam != null ? awayTeam.getId() : null)
        );

        return Map.of(
                "uploadUrl", presignedUrl,
                "matchId", match.getId().toString() // Frontend can use this right away!
        );
    }

    @Transactional
    public Match markMatchAsPreprocessing(String savedMinioFileName) {
        Match match = matchRepository.findBySavedMinioFileName(savedMinioFileName)
            .orElseThrow(() -> new NotFoundException("error.match.not_found_by_saved_minio_filename", new Object[]{savedMinioFileName}, "CRITICAL: Match record not found for: " + savedMinioFileName));

        match.setOverallStatus("PREPROCESSING");
        match.setFieldDetectionStatus("PENDING");
        matchRepository.save(match);
        log.info("Match {} status updated to PREPROCESSING (awaiting field detection)", match.getId());
        return match;
    }

    @Transactional
    public Match applyFieldDetectionResult(UUID matchId, String defishedImageUrl, String cornersJson) {
        Match match = matchRepository.findById(matchId)
            .orElseThrow(() -> new NotFoundException("error.match.not_found", new Object[]{matchId}, "Match not found: " + matchId));

        match.setDefishedImageUrl(defishedImageUrl);
        match.setFieldCornersJson(cornersJson);
        match.setFieldDetectionStatus("COMPLETED");
        match.setOverallStatus("AWAITING_CORNERS");
        matchRepository.save(match);
        log.info("Match {} field detection completed; awaiting user confirmation of corners", matchId);
        return match;
    }

    @Transactional
    public Match markFieldDetectionFailed(UUID matchId, String errorMessage) {
        Match match = matchRepository.findById(matchId)
            .orElseThrow(() -> new NotFoundException("error.match.not_found", new Object[]{matchId}, "Match not found: " + matchId));

        match.setFieldDetectionStatus("FAILED");
        match.setOverallStatus("ERROR");
        matchRepository.save(match);
        log.warn("Match {} field detection failed: {}", matchId, errorMessage);
        return match;
    }

    /**
     * Persists user-confirmed corners and transitions the match to PROCESSING.
     * Returns the Match so the orchestrator can publish the ML/encode messages.
     */
    @Transactional
    public Match confirmCornersAndStartProcessing(UUID matchId, List<Corner> corners, User actor) {
        Match match = matchRepository.findById(matchId)
            .orElseThrow(() -> new NotFoundException("error.match.not_found", new Object[]{matchId}, "Match not found: " + matchId));

        if (userAccessService != null && !userAccessService.canAccessMatch(actor, matchId)) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "User cannot modify this match");
        }

        if (!"AWAITING_CORNERS".equals(match.getOverallStatus())) {
            throw new BadRequestException("validation.match.corners.invalid_state", new Object[]{match.getOverallStatus()},
                    "Corners can only be confirmed while the match is in AWAITING_CORNERS state.");
        }

        if (corners == null || corners.size() != 4) {
            throw new BadRequestException("validation.match.corners.invalid_count", new Object[0],
                    "Exactly four corners are required.");
        }

        String cornersJson;
        try {
            cornersJson = objectMapper.writeValueAsString(corners);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new BadRequestException("validation.match.corners.invalid", new Object[0],
                    "Corners could not be serialized.");
        }

        match.setFieldCornersJson(cornersJson);
        match.setOverallStatus("PROCESSING");
        matchRepository.save(match);
        log.info("Match {} corners confirmed by user {}; transitioning to PROCESSING", matchId,
                actor != null ? actor.getId() : null);
        return match;
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatchDetails(UUID matchId) {
        log.debug("Fetching match details for ID: {}", matchId);
        Match match = matchRepository.findById(matchId)
            .orElseThrow(() -> new NotFoundException("error.match.not_found", new Object[]{matchId}, "Match not found: " + matchId));
        return toResponse(match);
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> getAllMatches(User actor) {
        log.debug("Fetching all matches");
        List<Match> matchesFromDb = resolveVisibleMatches(actor);
        if (matchesFromDb == null || matchesFromDb.isEmpty()) {
            log.info("Found 0 matches");
            return List.of();
        }

        List<MatchResponse> matches = matchesFromDb.stream().map(this::toResponse).toList();
        log.info("Found {} matches", matches.size());
        return matches;
    }

    // Backward compatible no-arg overload used in tests
    @Transactional(readOnly = true)
    public List<MatchResponse> getAllMatches() {
        return getAllMatches((User) null);
    }

    private List<Match> resolveVisibleMatches(User actor) {
        if (actor == null || actor.getRole() == null) {
            return List.of();
        }

        if (actor.getRole() == UserRole.ADMIN) {
            return matchRepository.findAll();
        }

        Set<UUID> teamIds = resolveActorTeamIds(actor);
        if (teamIds.isEmpty()) {
            return List.of();
        }

        // Deduplicate matches when a match contains two teams the actor is part of.
        Map<UUID, Match> uniqueMatches = teamIds.stream()
                .flatMap(teamId -> matchRepository.findAllByHomeTeam_IdOrAwayTeam_Id(teamId, teamId).stream())
                .collect(Collectors.toMap(
                        Match::getId,
                        match -> match,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));

        return List.copyOf(uniqueMatches.values());
    }

    private Set<UUID> resolveActorTeamIds(User actor) {
        if (actor == null) {
            return Set.of();
        }

        return switch (actor) {
            case Coach coach ->
                    new HashSet<>(coachRepository.findTeamIdsByCoachId(coach.getId()));

            case Player player ->
                    new HashSet<>(playerRepository.findTeamIdsByPlayerId(player.getId()));

            case Fan fan ->
                    fan.getTeams().stream()
                            .map(Team::getId)
                            .collect(Collectors.toCollection(LinkedHashSet::new));

            default -> {
                // Admin esetén üres halmazt adunk vissza, mert ő nem csapat-alapon látja a meccseket
                log.debug("No team-based filtering needed for actor type: {}", actor.getClass().getSimpleName());
                yield Set.of();
            }
        };
    }

    @Transactional
    public MatchResponse updateMatch(UUID matchId, UpdateMatchRequest request, User actor) {
        log.debug("Updating match details for ID: {}", matchId);
        Match match = matchRepository.findById(matchId)
            .orElseThrow(() -> new NotFoundException("error.match.not_found", new Object[]{matchId}, "Match not found: " + matchId));

        if (userAccessService != null && !userAccessService.canAccessMatch(actor, matchId)) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "User cannot modify this match");
        }

        if (request.homeTeamId() != null) {
            Team team = teamRepository.findById(request.homeTeamId())
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{request.homeTeamId()}, "Team not found"));
            match.setHomeTeam(team);
        }
        if (request.awayTeamId() != null) {
            Team team = teamRepository.findById(request.awayTeamId())
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{request.awayTeamId()}, "Team not found"));
            match.setAwayTeam(team);
        }

        if (request.matchDate() != null) match.setMatchDate(request.matchDate());
        if (request.homeScore() != null) match.setHomeScore(request.homeScore());
        if (request.awayScore() != null) match.setAwayScore(request.awayScore());

        matchRepository.save(match);
        return toResponse(match);
    }


    @Transactional
public MatchResponse updateTrackingLabelData(UUID matchId, UpdateTrackingLabelDataRequest request, User actor) {
    // 1. Meccs lekérése (hogy tudjuk, melyik fájlról van szó)
    Match match = matchRepository.findById(matchId)
        .orElseThrow(() -> new NotFoundException("error.match.not_found", new Object[]{matchId}, "Match not found: " + matchId));

    if (userAccessService != null && !userAccessService.canAccessMatch(actor, matchId)) {
        throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "User cannot modify this match");
    }

    JsonNode labelData = request != null ? request.labelData() : null;
    if (labelData == null || !labelData.isArray()) {
        throw new BadRequestException("validation.match.labelData.required", new Object[0], "labelData must be a JSON array.");
    }

    ParsedObjectLocation location = resolveTrackingLocation(matchId, match.getTrackingDataUrl());
    if (location == null) {
        throw new NotFoundException("error.match.tracking_not_found", new Object[]{matchId}, "Tracking data location not found for match: " + matchId);
    }

        // Beolvassuk a 200.000 sort, kicseréljük a címkéket, és visszatöltjük (streaming módon)
        patchLabelData(location.bucket(), location.key(), labelData);

    auditEventService.record(
            "TRACKING_LABEL_DATA_UPDATED",
            actor != null ? actor.getId() : null,
            actor != null ? actor.getUserRole() : null,
            "MATCH",
            matchId.toString(),
            "trackingBucket=" + location.bucket() + ", trackingKey=" + location.key()
    );
    
    return toResponse(match);
}

    @Transactional
    public void deleteMatch(UUID matchId, User actor) {
        log.debug("Attempting to delete match with ID: {}", matchId);
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new NotFoundException("error.match.not_found", new Object[]{matchId}, "Match not found: " + matchId));

        if (userAccessService != null) {
            userAccessService.canAccessMatch(actor, matchId);
        }

        // Előbb a kapcsolt clip rekordokat és a csapat-független meccsre mutató rekordokat töröljük,
        // majd a MinIO objektumokat takarítjuk el a megmaradt metaadatok alapján.
        List<Clip> clips = clipRepository.findAllByMatch_IdIn(List.of(matchId));

        clipRepository.deleteAllByMatch_IdIn(List.of(matchId));
        matchSquadMemberRepository.deleteAllByMatch_Id(matchId);
        matchRepository.delete(match);
        matchRepository.flush();

        minioObjectCleanupService.deleteMatchArtifacts(match, clips);
        auditEventService.record(
            "MATCH_DELETED",
            actor != null ? actor.getId() : null,
            actor != null ? actor.getUserRole() : null,
            "MATCH",
            matchId.toString(),
            "clipIds=" + formatUuidList(clips.stream().map(Clip::getId).toList())
        );
        log.info("Match successfully deleted: matchId={}, originalFileName={}, clipsDeleted={}",
                matchId,
                match.getOriginalFileName(),
                clips.size());
    }



    private String formatUuidList(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return "[]";
        }

        return ids.stream()
                .map(UUID::toString)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private ParsedObjectLocation resolveTrackingLocation(UUID matchId, String trackingDataUrl) {
        ParsedObjectLocation location = parseObjectLocation(trackingDataUrl);
        if (location != null) {
            return location;
        }

        if (matchId == null) {
            return null;
        }

        return new ParsedObjectLocation(trackingDataBucket, matchId + ".json");
    }

    private ObjectNode readTrackingPayload(String bucket, String key) {
        try (ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build())) {
            JsonNode root = objectMapper.readTree(stream);
            if (!(root instanceof ObjectNode objectNode)) {
                throw new BadRequestException("validation.match.tracking.invalid_format", new Object[0], "Tracking payload must be a JSON object.");
            }
            return objectNode;
        } catch (NoSuchKeyException ex) {
            throw new NotFoundException("error.match.tracking_not_found", new Object[]{key}, "Could not load tracking payload: " + bucket + "/" + key);
        } catch (java.io.IOException ex) {
            throw new BadRequestException("validation.match.tracking.invalid_format", new Object[0], "Tracking payload is not valid JSON.");
        } catch (Exception ex) {
            throw new ExternalServiceException("Could not load tracking payload from storage.", ex);
        }
    }

    private void writeTrackingPayload(String bucket, String key, ObjectNode payload) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("application/json")
                    .build();
            s3Client.putObject(putRequest, software.amazon.awssdk.core.sync.RequestBody.fromString(objectMapper.writeValueAsString(payload)));
        } catch (Exception ex) {
            throw new ExternalServiceException("Could not save tracking payload.", ex);
        }
    }

    // Streaming patch: read JSON from S3, copy fields except labelData, append new labelData and upload
    private void patchLabelData(String bucket, String key, JsonNode newLabelData) {
        try (ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build())) {

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            try (JsonParser parser = objectMapper.getFactory().createParser(stream);
                 JsonGenerator generator = objectMapper.getFactory().createGenerator(out)) {

                // Gyökér objektum nyitása
                if (parser.nextToken() != JsonToken.START_OBJECT) {
                    throw new BadRequestException("validation.match.tracking.invalid_format",
                            new Object[0], "Tracking payload must be a JSON object.");
                }
                generator.writeStartObject();

                // Mezők másolása, labelData kihagyásával
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = parser.currentName();
                    parser.nextToken(); // move to value

                    if ("labelData".equals(fieldName)) {
                        parser.skipChildren(); // kihagyjuk a régit
                    } else {
                        generator.writeFieldName(fieldName);
                        generator.copyCurrentStructure(parser);
                    }
                }

                // Új labelData hozzáfűzése
                generator.writeFieldName("labelData");
                generator.writeTree(newLabelData);

                generator.writeEndObject();
            }

            // Visszaírás S3-ba
            byte[] result = out.toByteArray();
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType("application/json")
                            .contentLength((long) result.length)
                            .build(),
                    software.amazon.awssdk.core.sync.RequestBody.fromBytes(result));

        } catch (NoSuchKeyException ex) {
            throw new NotFoundException("error.match.tracking_not_found",
                    new Object[]{key}, "Could not load tracking payload: " + bucket + "/" + key);
        } catch (IOException ex) {
            throw new BadRequestException("validation.match.tracking.invalid_format",
                    new Object[0], "Tracking payload is not valid JSON.");
        } catch (Exception ex) {
            throw new ExternalServiceException("Could not patch tracking payload.", ex);
        }
    }

    private ParsedObjectLocation parseObjectLocation(String objectUrl) {
        if (objectUrl == null || objectUrl.isBlank()) {
            return null;
        }

        try {
            java.net.URI uri = java.net.URI.create(objectUrl);
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

    private MatchResponse toResponse(Match match) {
        UUID homeTeamId     = match.getHomeTeam() != null ? match.getHomeTeam().getId()   : null;
        String homeTeamName = match.getHomeTeam() != null ? match.getHomeTeam().getName() : null;
        UUID awayTeamId     = match.getAwayTeam() != null ? match.getAwayTeam().getId()   : null;
        String awayTeamName = match.getAwayTeam() != null ? match.getAwayTeam().getName() : match.getAwayTeamName();

        List<Corner> fieldCorners = parseCorners(match.getFieldCornersJson());

        return new MatchResponse(
                match.getId(),
                homeTeamId,     homeTeamName,
                awayTeamId,     awayTeamName,
                match.getHomeTeamColor(),
                match.getAwayTeamColor(),
                match.getRefereeColor(),
                match.getMatchDate(),
                match.getHomeScore(),
                match.getAwayScore(),
                match.getOriginalFileName(),
                match.getHlsManifestUrl(),
                match.getTrackingDataUrl(),
                match.getDefishedImageUrl(),
                fieldCorners,
                match.getFieldDetectionStatus(),
                match.getOverallStatus(),
                match.getMlStatus(),
                match.getEncodingStatus(),
                match.getCreatedAt()
        );
    }

    private List<Corner> parseCorners(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Corner.class));
        } catch (IOException ex) {
            log.warn("Failed to parse stored field corners JSON: {}", ex.getMessage());
            return null;
        }
    }

    private record ParsedObjectLocation(String bucket, String key) {}

    private String normalizeRequiredColor(String value) {
        if (value == null) {
            throw new BadRequestException("validation.match.color.required", new Object[0], "Match shirt color is required.");
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new BadRequestException("validation.match.color.required", new Object[0], "Match shirt color is required.");
        }

        return normalized;
    }

    private String normalizeOptionalTeamName(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}