package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.*;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.requests.CreateCupRequest;
import com.example.footballanalysis.model.requests.CupTeamCreateRequest;
import com.example.footballanalysis.model.requests.CupMatchScheduleRequest;
import com.example.footballanalysis.model.requests.UpdateMatchScoreRequest;
import com.example.footballanalysis.model.responses.*;
import com.example.footballanalysis.repository.CupMatchRepository;
import com.example.footballanalysis.repository.CupRepository;
import com.example.footballanalysis.repository.CupTeamRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CupService {

    private final CupRepository cupRepository;
    private final CupTeamRepository cupTeamRepository;
    private final CupMatchRepository cupMatchRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final AuditEventService auditEventService;

    /**
     * Új kupa létrehozása
     */
    @Transactional
    public CupResponse createCup(CreateCupRequest request, User creator) {
        // Validáció
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestException(
                    "error.validation.invalid_request",
                    new Object[]{"name"},
                    "Cup name cannot be empty"
            );
        }

        // Cup entitás létrehozása
        Cup cup = new Cup();
        cup.setName(request.name());
        cup.setCreatedBy(creator);

        Cup savedCup = cupRepository.save(cup);

        // Audit log
        String traceId = MDC.get("traceId");
        log.atInfo()
                .setMessage("Cup created: cupId={}, creatorId={}")
                .addArgument(savedCup.getId())
                .addArgument(creator.getId())
                .addKeyValue("event_type", "CUP_CREATED")
                .addKeyValue("cup_id", savedCup.getId())
                .addKeyValue("creator_id", creator.getId())
                .addKeyValue("trace_id", traceId)
                .log();

        auditEventService.record(
                "CUP_CREATED",
                creator.getId(),
                creator.getUserRole(),
                "CUP",
                savedCup.getId().toString(),
                String.format("Cup '%s' created", savedCup.getName())
        );

        return mapCupToResponse(savedCup);
    }

    /**
     * Kupa lekérése azonosító alapján
     */
    @Transactional(readOnly = true)
    public CupResponse getCup(UUID cupId) {
        Cup cup = cupRepository.findById(cupId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cup.not_found",
                        new Object[]{cupId},
                        "Cup not found: " + cupId
                ));
        return mapCupToResponse(cup);
    }

    /**
     * Csapat hozzáadása a kupához
     * Csak a cup creator-ja lehet
     */
    @Transactional
    public CupTeamResponse addTeamToCup(UUID cupId, CupTeamCreateRequest request, User actor) {
        // Cup létezésének és jogosultságének ellenőrzése
        Cup cup = cupRepository.findById(cupId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cup.not_found",
                        new Object[]{cupId},
                        "Cup not found: " + cupId
                ));

        if (!cup.getCreatedBy().getId().equals(actor.getId())) {
            throw new UnauthorizedException(
                    "error.auth.forbidden",
                    new Object[0],
                    "Only cup creator can add teams"
            );
        }

        // Validáció
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestException(
                    "error.validation.invalid_request",
                    new Object[]{"name"},
                    "Team name cannot be empty"
            );
        }

        // Ha realTeamId meg van adva, ellenőrzés
        Team realTeam = null;
        if (request.realTeamId() != null) {
            realTeam = teamRepository.findById(request.realTeamId())
                    .orElseThrow(() -> new NotFoundException(
                            "error.team.not_found",
                            new Object[]{request.realTeamId()},
                            "Team not found: " + request.realTeamId()
                    ));
        }

        // CupTeam létrehozása
        CupTeam cupTeam = new CupTeam();
        cupTeam.setCup(cup);
        cupTeam.setName(request.name());
        cupTeam.setRealTeam(realTeam);

        CupTeam savedTeam = cupTeamRepository.save(cupTeam);

        // Audit log
        String traceId = MDC.get("traceId");
        log.atInfo()
                .setMessage("Team added to cup: cupId={}, teamId={}, teamName={}")
                .addArgument(cupId)
                .addArgument(savedTeam.getId())
                .addArgument(request.name())
                .addKeyValue("event_type", "TEAM_ADDED")
                .addKeyValue("cup_id", cupId)
                .addKeyValue("team_id", savedTeam.getId())
                .addKeyValue("actor_id", actor.getId())
                .addKeyValue("trace_id", traceId)
                .log();

        auditEventService.record(
                "TEAM_ADDED",
                actor.getId(),
                actor.getUserRole(),
                "CUP_TEAM",
                savedTeam.getId().toString(),
                String.format("Team '%s' added to cup '%s'", request.name(), cup.getName())
        );

        return mapCupTeamToResponse(savedTeam);
    }

    /**
     * Meccs ütemezése a kupához
     * Csak a cup creator-ja lehet
     */
    @Transactional
    public CupMatchResponse scheduleMatch(UUID cupId, CupMatchScheduleRequest request, User actor) {
        // Cup létezésének és jogosultságának ellenőrzése
        Cup cup = cupRepository.findById(cupId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cup.not_found",
                        new Object[]{cupId},
                        "Cup not found: " + cupId
                ));

        if (!cup.getCreatedBy().getId().equals(actor.getId())) {
            throw new UnauthorizedException(
                    "error.auth.forbidden",
                    new Object[0],
                    "Only cup creator can schedule matches"
            );
        }

        // Csapatok létezésének ellenőrzése
        CupTeam homeTeam = cupTeamRepository.findById(request.homeTeamId())
                .orElseThrow(() -> new NotFoundException(
                        "error.cupteam.not_found",
                        new Object[]{request.homeTeamId()},
                        "Home team not found"
                ));

        CupTeam awayTeam = cupTeamRepository.findById(request.awayTeamId())
                .orElseThrow(() -> new NotFoundException(
                        "error.cupteam.not_found",
                        new Object[]{request.awayTeamId()},
                        "Away team not found"
                ));

        // Validáció: home != away
        if (request.homeTeamId().equals(request.awayTeamId())) {
            throw new BadRequestException(
                    "error.validation.invalid_request",
                    new Object[]{"teams"},
                    "Home team and away team cannot be the same"
            );
        }

        // Validáció: nincs már ilyen mérkőzés
        if (cupMatchRepository.existsByCup_IdAndHomeTeam_IdAndAwayTeam_Id(
                cupId, request.homeTeamId(), request.awayTeamId())) {
            throw new BadRequestException(
                    "error.validation.duplicate_match",
                    new Object[]{},
                    "This match already exists in the cup"
            );
        }

        // CupMatch létrehozása
        CupMatch match = new CupMatch();
        match.setCup(cup);
        match.setHomeTeam(homeTeam);
        match.setAwayTeam(awayTeam);
        match.setScheduledAt(request.scheduledAt());
        match.setPlayed(false);

        CupMatch savedMatch = cupMatchRepository.save(match);

        // Audit log
        String traceId = MDC.get("traceId");
        log.atInfo()
                .setMessage("Match scheduled: cupId={}, matchId={}, home={}, away={}")
                .addArgument(cupId)
                .addArgument(savedMatch.getId())
                .addArgument(homeTeam.getName())
                .addArgument(awayTeam.getName())
                .addKeyValue("event_type", "MATCH_SCHEDULED")
                .addKeyValue("cup_id", cupId)
                .addKeyValue("match_id", savedMatch.getId())
                .addKeyValue("actor_id", actor.getId())
                .addKeyValue("trace_id", traceId)
                .log();

        auditEventService.record(
                "MATCH_SCHEDULED",
                actor.getId(),
                actor.getUserRole(),
                "CUP_MATCH",
                savedMatch.getId().toString(),
                String.format("Match scheduled: %s vs %s in cup '%s'",
                        homeTeam.getName(), awayTeam.getName(), cup.getName())
        );

        return mapCupMatchToResponse(savedMatch);
    }

    /**
     * Meccs eredményének rögzítése
     * Csak a cup creator-ja lehet
     */
    @Transactional
    public CupMatchResponse recordScore(UUID cupId, UUID matchId, UpdateMatchScoreRequest request, User actor) {
        // Cup és match létezésének ellenőrzése
        Cup cup = cupRepository.findById(cupId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cup.not_found",
                        new Object[]{cupId},
                        "Cup not found: " + cupId
                ));

        if (!cup.getCreatedBy().getId().equals(actor.getId())) {
            throw new UnauthorizedException(
                    "error.auth.forbidden",
                    new Object[0],
                    "Only cup creator can record scores"
            );
        }

        CupMatch match = cupMatchRepository.findByIdAndCup_Id(matchId, cupId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cupmatch.not_found",
                        new Object[]{matchId},
                        "Match not found in this cup"
                ));

        // Score validáció
        if (request.homeScore() < 0 || request.awayScore() < 0) {
            throw new BadRequestException(
                    "error.validation.negative_score",
                    new Object[]{},
                    "Scores cannot be negative"
            );
        }

        // Score frissítése
        match.setHomeScore(request.homeScore());
        match.setAwayScore(request.awayScore());
        match.setPlayed(true);

        CupMatch updatedMatch = cupMatchRepository.save(match);

        // Audit log
        String traceId = MDC.get("traceId");
        log.atInfo()
                .setMessage("Score recorded: cupId={}, matchId={}, score={}-{}")
                .addArgument(cupId)
                .addArgument(matchId)
                .addArgument(request.homeScore())
                .addArgument(request.awayScore())
                .addKeyValue("event_type", "CUP_MATCH_RESULT_UPDATED")
                .addKeyValue("cup_id", cupId)
                .addKeyValue("match_id", matchId)
                .addKeyValue("home_score", request.homeScore())
                .addKeyValue("away_score", request.awayScore())
                .addKeyValue("actor_id", actor.getId())
                .addKeyValue("trace_id", traceId)
                .log();

        auditEventService.record(
                "CUP_MATCH_RESULT_UPDATED",
                actor.getId(),
                actor.getUserRole(),
                "CUP_MATCH",
                matchId.toString(),
                String.format("Score recorded: %s %d-%d %s in cup '%s'",
                        match.getHomeTeam().getName(),
                        request.homeScore(),
                        request.awayScore(),
                        match.getAwayTeam().getName(),
                        cup.getName())
        );

        return mapCupMatchToResponse(updatedMatch);
    }

    /**
     * Valós meccs összekapcsolása a Cup meccshez
     */
    @Transactional
    public CupMatchResponse linkRealMatch(UUID cupId, UUID matchId, UUID realMatchId, User actor) {
        // Cup és match létezésének ellenőrzése
        Cup cup = cupRepository.findById(cupId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cup.not_found",
                        new Object[]{cupId},
                        "Cup not found: " + cupId
                ));

        if (!cup.getCreatedBy().getId().equals(actor.getId())) {
            throw new UnauthorizedException(
                    "error.auth.forbidden",
                    new Object[0],
                    "Only cup creator can link real matches"
            );
        }

        CupMatch match = cupMatchRepository.findByIdAndCup_Id(matchId, cupId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cupmatch.not_found",
                        new Object[]{matchId},
                        "Match not found in this cup"
                ));

        // Real match létezésének ellenőrzése
        Match realMatch = null;
        if (realMatchId != null) {
            realMatch = matchRepository.findById(realMatchId)
                    .orElseThrow(() -> new NotFoundException(
                            "error.match.not_found",
                            new Object[]{realMatchId},
                            "Match not found: " + realMatchId
                    ));
        }

        match.setRealMatch(realMatch);
        CupMatch updatedMatch = cupMatchRepository.save(match);

        // Audit log
        String traceId = MDC.get("traceId");
        log.atInfo()
                .setMessage("Real match linked: cupId={}, matchId={}, realMatchId={}")
                .addArgument(cupId)
                .addArgument(matchId)
                .addArgument(realMatchId)
                .addKeyValue("event_type", "CUP_MATCH_LINKED")
                .addKeyValue("cup_id", cupId)
                .addKeyValue("match_id", matchId)
                .addKeyValue("real_match_id", realMatchId)
                .addKeyValue("actor_id", actor.getId())
                .addKeyValue("trace_id", traceId)
                .log();

        auditEventService.record(
                "CUP_MATCH_LINKED",
                actor.getId(),
                actor.getUserRole(),
                "CUP_MATCH",
                matchId.toString(),
                String.format("Real match linked to cup match in cup '%s'", cup.getName())
        );

        return mapCupMatchToResponse(updatedMatch);
    }

    /**
     * Kupacsapat módosítása (név vagy realTeam módosítása/unlink)
     * Csak cup creator-ja lehet
     */
    @Transactional
    public CupTeamResponse updateTeam(UUID cupId, UUID teamId, CupTeamCreateRequest request, User actor) {
        Cup cup = cupRepository.findById(cupId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cup.not_found",
                        new Object[]{cupId},
                        "Cup not found: " + cupId
                ));

        if (!cup.getCreatedBy().getId().equals(actor.getId())) {
            throw new UnauthorizedException(
                    "error.auth.forbidden",
                    new Object[0],
                    "Only cup creator can modify teams"
            );
        }

        CupTeam team = cupTeamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cupteam.not_found",
                        new Object[]{teamId},
                        "Cup team not found: " + teamId
                ));

        if (!team.getCup().getId().equals(cupId)) {
            throw new BadRequestException(
                    "error.validation.invalid_request",
                    new Object[]{},
                    "Team does not belong to the specified cup"
            );
        }

        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestException(
                    "error.validation.invalid_request",
                    new Object[]{"name"},
                    "Team name cannot be empty"
            );
        }

        team.setName(request.name());

        // realTeamId can be null => unlink
        if (request.realTeamId() != null) {
            Team realTeam = teamRepository.findById(request.realTeamId())
                    .orElseThrow(() -> new NotFoundException(
                            "error.team.not_found",
                            new Object[]{request.realTeamId()},
                            "Team not found: " + request.realTeamId()
                    ));
            team.setRealTeam(realTeam);
        } else {
            team.setRealTeam(null);
        }

        CupTeam updated = cupTeamRepository.save(team);

        String traceId = MDC.get("traceId");
        log.atInfo()
                .setMessage("Cup team updated: cupId={}, teamId={}, name={}")
                .addArgument(cupId)
                .addArgument(teamId)
                .addArgument(request.name())
                .addKeyValue("event_type", "CUP_TEAM_UPDATED")
                .addKeyValue("cup_id", cupId)
                .addKeyValue("team_id", teamId)
                .addKeyValue("actor_id", actor.getId())
                .addKeyValue("trace_id", traceId)
                .log();

        auditEventService.record(
                "CUP_TEAM_UPDATED",
                actor.getId(),
                actor.getUserRole(),
                "CUP_TEAM",
                teamId.toString(),
                String.format("Team '%s' updated in cup '%s'", request.name(), cup.getName())
        );

        return mapCupTeamToResponse(updated);
    }

    /**
     * Kupacsapat törlése
     */
    @Transactional
    public void deleteTeam(UUID cupId, UUID teamId, User actor) {
        Cup cup = cupRepository.findById(cupId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cup.not_found",
                        new Object[]{cupId},
                        "Cup not found: " + cupId
                ));

        if (!cup.getCreatedBy().getId().equals(actor.getId())) {
            throw new UnauthorizedException(
                    "error.auth.forbidden",
                    new Object[0],
                    "Only cup creator can delete teams"
            );
        }

        CupTeam team = cupTeamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cupteam.not_found",
                        new Object[]{teamId},
                        "Cup team not found: " + teamId
                ));

        if (!team.getCup().getId().equals(cupId)) {
            throw new BadRequestException(
                    "error.validation.invalid_request",
                    new Object[]{},
                    "Team does not belong to the specified cup"
            );
        }

        // Ne töröljük, ha bármely meccs hivatkozik rá
        long referencedMatches = cupMatchRepository.countByCupIdAndTeamId(cupId, teamId);
        if (referencedMatches > 0) {
            throw new BadRequestException(
                    "error.cupteam.referenced_by_matches",
                    new Object[]{referencedMatches},
                    "Cannot delete team because it is referenced by " + referencedMatches + " match(es)"
            );
        }

        cupTeamRepository.delete(team);

        String traceId = MDC.get("traceId");
        log.atInfo()
                .setMessage("Cup team deleted: cupId={}, teamId={}")
                .addArgument(cupId)
                .addArgument(teamId)
                .addKeyValue("event_type", "CUP_TEAM_DELETED")
                .addKeyValue("cup_id", cupId)
                .addKeyValue("team_id", teamId)
                .addKeyValue("actor_id", actor.getId())
                .addKeyValue("trace_id", traceId)
                .log();

        auditEventService.record(
                "CUP_TEAM_DELETED",
                actor.getId(),
                actor.getUserRole(),
                "CUP_TEAM",
                teamId.toString(),
                String.format("Team '%s' deleted from cup '%s'", team.getName(), cup.getName())
        );
    }

    /**
     * Meccs módosítása (teams/scheduledAt)
     */
    @Transactional
    public CupMatchResponse updateMatch(UUID cupId, UUID matchId, CupMatchScheduleRequest request, User actor) {
        Cup cup = cupRepository.findById(cupId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cup.not_found",
                        new Object[]{cupId},
                        "Cup not found: " + cupId
                ));

        if (!cup.getCreatedBy().getId().equals(actor.getId())) {
            throw new UnauthorizedException(
                    "error.auth.forbidden",
                    new Object[0],
                    "Only cup creator can modify matches"
            );
        }

        CupMatch match = cupMatchRepository.findByIdAndCup_Id(matchId, cupId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cupmatch.not_found",
                        new Object[]{matchId},
                        "Match not found in this cup"
                ));

        CupTeam homeTeam = cupTeamRepository.findById(request.homeTeamId())
                .orElseThrow(() -> new NotFoundException(
                        "error.cupteam.not_found",
                        new Object[]{request.homeTeamId()},
                        "Home team not found"
                ));

        CupTeam awayTeam = cupTeamRepository.findById(request.awayTeamId())
                .orElseThrow(() -> new NotFoundException(
                        "error.cupteam.not_found",
                        new Object[]{request.awayTeamId()},
                        "Away team not found"
                ));

        if (!homeTeam.getCup().getId().equals(cupId) || !awayTeam.getCup().getId().equals(cupId)) {
            throw new BadRequestException(
                    "error.validation.invalid_request",
                    new Object[]{},
                    "Teams must belong to the same cup"
            );
        }

        if (request.homeTeamId().equals(request.awayTeamId())) {
            throw new BadRequestException(
                    "error.validation.invalid_request",
                    new Object[]{"teams"},
                    "Home team and away team cannot be the same"
            );
        }

        // Duplicate check: find any existing match with same teams
        Optional<CupMatch> existing = cupMatchRepository.findByCup_IdAndHomeTeam_IdAndAwayTeam_Id(cupId, request.homeTeamId(), request.awayTeamId());
        if (existing.isPresent() && !existing.get().getId().equals(matchId)) {
            throw new BadRequestException(
                    "error.validation.duplicate_match",
                    new Object[]{},
                    "This match already exists in the cup"
            );
        }

        match.setHomeTeam(homeTeam);
        match.setAwayTeam(awayTeam);
        match.setScheduledAt(request.scheduledAt());

        CupMatch updated = cupMatchRepository.save(match);

        String traceId = MDC.get("traceId");
        log.atInfo()
                .setMessage("Cup match updated: cupId={}, matchId={}")
                .addArgument(cupId)
                .addArgument(matchId)
                .addKeyValue("event_type", "CUP_MATCH_UPDATED")
                .addKeyValue("cup_id", cupId)
                .addKeyValue("match_id", matchId)
                .addKeyValue("actor_id", actor.getId())
                .addKeyValue("trace_id", traceId)
                .log();

        auditEventService.record(
                "CUP_MATCH_UPDATED",
                actor.getId(),
                actor.getUserRole(),
                "CUP_MATCH",
                matchId.toString(),
                String.format("Match updated in cup '%s'", cup.getName())
        );

        return mapCupMatchToResponse(updated);
    }

    /**
     * Meccs törlése
     */
    @Transactional
    public void deleteMatch(UUID cupId, UUID matchId, User actor) {
        Cup cup = cupRepository.findById(cupId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cup.not_found",
                        new Object[]{cupId},
                        "Cup not found: " + cupId
                ));

        if (!cup.getCreatedBy().getId().equals(actor.getId())) {
            throw new UnauthorizedException(
                    "error.auth.forbidden",
                    new Object[0],
                    "Only cup creator can delete matches"
            );
        }

        CupMatch match = cupMatchRepository.findByIdAndCup_Id(matchId, cupId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cupmatch.not_found",
                        new Object[]{matchId},
                        "Match not found in this cup"
                ));

        cupMatchRepository.delete(match);

        String traceId = MDC.get("traceId");
        log.atInfo()
                .setMessage("Cup match deleted: cupId={}, matchId={}")
                .addArgument(cupId)
                .addArgument(matchId)
                .addKeyValue("event_type", "CUP_MATCH_DELETED")
                .addKeyValue("cup_id", cupId)
                .addKeyValue("match_id", matchId)
                .addKeyValue("actor_id", actor.getId())
                .addKeyValue("trace_id", traceId)
                .log();

        auditEventService.record(
                "CUP_MATCH_DELETED",
                actor.getId(),
                actor.getUserRole(),
                "CUP_MATCH",
                matchId.toString(),
                String.format("Match deleted from cup '%s'", cup.getName())
        );
    }

    /**
     * Real match unlink (set to null)
     */
    @Transactional
    public CupMatchResponse unlinkRealMatch(UUID cupId, UUID matchId, User actor) {
        Cup cup = cupRepository.findById(cupId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cup.not_found",
                        new Object[]{cupId},
                        "Cup not found: " + cupId
                ));

        if (!cup.getCreatedBy().getId().equals(actor.getId())) {
            throw new UnauthorizedException(
                    "error.auth.forbidden",
                    new Object[0],
                    "Only cup creator can unlink real matches"
            );
        }

        CupMatch match = cupMatchRepository.findByIdAndCup_Id(matchId, cupId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cupmatch.not_found",
                        new Object[]{matchId},
                        "Match not found in this cup"
                ));

        match.setRealMatch(null);
        CupMatch updated = cupMatchRepository.save(match);

        String traceId = MDC.get("traceId");
        log.atInfo()
                .setMessage("Real match unlinked: cupId={}, matchId={}")
                .addArgument(cupId)
                .addArgument(matchId)
                .addKeyValue("event_type", "CUP_MATCH_UNLINKED")
                .addKeyValue("cup_id", cupId)
                .addKeyValue("match_id", matchId)
                .addKeyValue("actor_id", actor.getId())
                .addKeyValue("trace_id", traceId)
                .log();

        auditEventService.record(
                "CUP_MATCH_UNLINKED",
                actor.getId(),
                actor.getUserRole(),
                "CUP_MATCH",
                matchId.toString(),
                String.format("Real match unlinked in cup '%s'", cup.getName())
        );

        return mapCupMatchToResponse(updated);
    }

    /**
     * Az összes kupa listázása
     */
    @Transactional(readOnly = true)
    public List<CupResponse> listAllCups() {
        return cupRepository.findAll().stream()
                .map(this::mapCupToResponse)
                .collect(Collectors.toList());
    }

    // ===== Mapping utilities =====

    private CupResponse mapCupToResponse(Cup cup) {
        // Simple user response with just basic info
        UserResponse createdByResponse = new UserResponse(
                cup.getCreatedBy().getId(),
                cup.getCreatedBy().getEmail(),
                cup.getCreatedBy().getFirstName(),
                cup.getCreatedBy().getLastName(),
                cup.getCreatedBy().getRole(),
                cup.getCreatedBy().getCreatedAt(),
                null  // teams - not needed for cup creator info
        );

        Set<CupTeamResponse> teamResponses = cup.getTeams().stream()
                .map(this::mapCupTeamToResponse)
                .collect(Collectors.toSet());

        Set<CupMatchResponse> matchResponses = cup.getMatches().stream()
                .map(this::mapCupMatchToResponse)
                .collect(Collectors.toSet());

        return new CupResponse(
                cup.getId(),
                cup.getName(),
                cup.getCreatedAt(),
                createdByResponse,
                teamResponses,
                matchResponses
        );
    }

    private CupTeamResponse mapCupTeamToResponse(CupTeam team) {
        TeamResponse realTeamResponse = null;
        if (team.getRealTeam() != null) {
            realTeamResponse = new TeamResponse(
                    team.getRealTeam().getId(),
                    team.getRealTeam().getName(),
                    team.getRealTeam().getShortName(),
                    team.getRealTeam().getLogoUrl(),
                    null,  // players - not needed for simple team response
                    null   // coaches - not needed for simple team response
            );
        }

        return new CupTeamResponse(
                team.getId(),
                team.getName(),
                realTeamResponse,
                team.getCreatedAt()
        );
    }

    private CupMatchResponse mapCupMatchToResponse(CupMatch match) {
        CupTeamResponse homeTeamResponse = mapCupTeamToResponse(match.getHomeTeam());
        CupTeamResponse awayTeamResponse = mapCupTeamToResponse(match.getAwayTeam());

        UUID realMatchId = match.getRealMatch() != null ? match.getRealMatch().getId() : null;

        return new CupMatchResponse(
                match.getId(),
                homeTeamResponse,
                awayTeamResponse,
                match.getHomeScore(),
                match.getAwayScore(),
                match.isPlayed(),
                match.getScheduledAt(),
                realMatchId,
                match.getCreatedAt()
        );
    }
}




