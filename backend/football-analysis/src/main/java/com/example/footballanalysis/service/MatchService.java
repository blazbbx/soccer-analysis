package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.requests.UpdateMatchRequest;
import com.example.footballanalysis.model.requests.UploadMatchRequest;
import com.example.footballanalysis.model.responses.MatchResponse;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.MatchSquadMemberRepository;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.repository.UserRepository;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.user.Fan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchService {

    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;
    private final ClipRepository clipRepository;
    private final MatchSquadMemberRepository matchSquadMemberRepository;
    private final UserRepository userRepository;
    private final S3PresignerService videoStorageService;
    private final MinioObjectCleanupService minioObjectCleanupService;
    private final AuditEventService auditEventService;
    private UserAccessService userAccessService; // optional; guarded when used

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
        String homeTeamShortsColor = normalizeOptionalColor(request.homeTeamShortsColor());
        String homeTeamSocksColor = normalizeOptionalColor(request.homeTeamSocksColor());
        String awayTeamShortsColor = normalizeOptionalColor(request.awayTeamShortsColor());
        String awayTeamSocksColor = normalizeOptionalColor(request.awayTeamSocksColor());
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
        match.setHomeTeamShortsColor(homeTeamShortsColor);
        match.setHomeTeamSocksColor(homeTeamSocksColor);
        match.setAwayTeamShortsColor(awayTeamShortsColor);
        match.setAwayTeamSocksColor(awayTeamSocksColor);


        String extension = "";
        int i = originalFilename.lastIndexOf('.');
        if (i > 0) extension = originalFilename.substring(i);
        String safeMinioName = String.valueOf(match.getId()) + extension;

        match.setOriginalFileName(originalFilename);
        match.setSavedMinioFileName(safeMinioName);
        match.setOverallStatus("UPLOADING");
        match.setMlStatus("PENDING");
        match.setEncodingStatus("PENDING");

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

    @Transactional(readOnly = true)
    public void markMatchAsProcessing(String savedMinioFileName) {
        Match match = matchRepository.findBySavedMinioFileName(savedMinioFileName)
            .orElseThrow(() -> new NotFoundException("error.match.not_found_by_saved_minio_filename", new Object[]{savedMinioFileName}, "CRITICAL: Match record not found for: " + savedMinioFileName));

        match.setOverallStatus("PROCESSING");
        matchRepository.save(match);
        log.info("Match {} status updated to PROCESSING", match.getId());
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatchDetails(UUID matchId) {
        log.debug("Fetching match details for ID: {}", matchId);
        Match match = matchRepository.findById(matchId)
            .orElseThrow(() -> {
                return new NotFoundException("error.match.not_found", new Object[]{matchId}, "Match not found: " + matchId);
            });
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
        return switch (actor.getRole()) {
            case COACH -> actor instanceof Coach coach
                    ? coach.getTeams().stream().map(Team::getId).collect(Collectors.toSet())
                    : Set.of();
            case PLAYER -> actor instanceof Player player
                    ? player.getTeams().stream().map(Team::getId).collect(Collectors.toSet())
                    : Set.of();
            case FAN -> actor instanceof Fan fan
                    ? fan.getTeams().stream().map(Team::getId).collect(Collectors.toSet())
                    : Set.of();
            case ADMIN -> Set.of();
        };
    }

    @Transactional
    public MatchResponse updateMatch(UUID matchId, UpdateMatchRequest request, User actor) {
        log.debug("Updating match details for ID: {}", matchId);
        Match match = matchRepository.findById(matchId)
            .orElseThrow(() -> new NotFoundException("error.match.not_found", new Object[]{matchId}, "Match not found: " + matchId));

        userAccessService.canAccessMatch(actor, matchId);

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
    public void deleteMatch(UUID matchId, User actor) {
        log.debug("Attempting to delete match with ID: {}", matchId);
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> {
                    return new NotFoundException("error.match.not_found", new Object[]{matchId}, "Match not found: " + matchId);
                });

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

    private MatchResponse toResponse(Match match) {
        UUID homeTeamId     = match.getHomeTeam() != null ? match.getHomeTeam().getId()   : null;
        String homeTeamName = match.getHomeTeam() != null ? match.getHomeTeam().getName() : null;
        UUID awayTeamId     = match.getAwayTeam() != null ? match.getAwayTeam().getId()   : null;
        String awayTeamName = match.getAwayTeam() != null ? match.getAwayTeam().getName() : match.getAwayTeamName();

        return new MatchResponse(
                match.getId(),
                homeTeamId,     homeTeamName,
                awayTeamId,     awayTeamName,
                match.getHomeTeamColor(),
                match.getAwayTeamColor(),
                match.getRefereeColor(),
                match.getHomeTeamShortsColor(),
                match.getHomeTeamSocksColor(),
                match.getAwayTeamShortsColor(),
                match.getAwayTeamSocksColor(),
                match.getMatchDate(),
                match.getHomeScore(),
                match.getAwayScore(),
                match.getOriginalFileName(),
                match.getHlsManifestUrl(),
                match.getTrackingDataUrl(),
                match.getOverallStatus(),
                match.getMlStatus(),
                match.getEncodingStatus(),
                match.getCreatedAt()
        );
    }

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

    private String normalizeOptionalColor(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeOptionalTeamName(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}