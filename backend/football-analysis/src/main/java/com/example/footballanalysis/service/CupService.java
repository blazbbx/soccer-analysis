package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.*;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
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
import com.example.footballanalysis.paging.PageQuery;
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
    private final UserAccessService userAccessService;

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

        return mapCupToResponse(savedCup, creator);
    }

    /**
     * Kupa lekérése azonosító alapján
     */
    @Transactional(readOnly = true)
    public CupResponse getCup(UUID cupId, User actor) {
        return getCup(cupId, actor, null);
    }

    @Transactional(readOnly = true)
    public CupResponse getCup(UUID cupId, User actor, UserRole view) {
        Cup cup = cupRepository.findById(cupId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cup.not_found",
                        new Object[]{cupId},
                        "Cup not found: " + cupId
                ));
        requireCupViewer(cup, actor, view);
        return mapCupToResponse(cup, actor);
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

        requireCupManager(cup, actor);

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

        requireCupManager(cup, actor);

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

        requireTeamsBelongToCup(cupId, homeTeam, awayTeam);

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

        return mapCupMatchToResponse(savedMatch, actor);
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

        requireCupManager(cup, actor);

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

        return mapCupMatchToResponse(updatedMatch, actor);
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

        requireCupManager(cup, actor);

        CupMatch match = cupMatchRepository.findByIdAndCup_Id(matchId, cupId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cupmatch.not_found",
                        new Object[]{matchId},
                        "Match not found in this cup"
                ));

        Match realMatch = null;
        if (realMatchId != null) {
            realMatch = matchRepository.findById(realMatchId)
                    .orElseThrow(() -> new NotFoundException(
                            "error.match.not_found",
                            new Object[]{realMatchId},
                            "Match not found: " + realMatchId
                    ));
            userAccessService.requireMatchAccess(actor, realMatchId);
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

        return mapCupMatchToResponse(updatedMatch, actor);
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

        requireCupManager(cup, actor);

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

        requireCupManager(cup, actor);

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

        requireCupManager(cup, actor);

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

        requireTeamsBelongToCup(cupId, homeTeam, awayTeam);

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

        return mapCupMatchToResponse(updated, actor);
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

        requireCupManager(cup, actor);

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

        requireCupManager(cup, actor);

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

        return mapCupMatchToResponse(updated, actor);
    }

    /**
     * Az összes kupa listázása
     */
    @Transactional(readOnly = true)
    public PageResponse<CupListItem> listAllCups(User actor, UserRole view, List<UUID> requestedTeamIds, Integer page, Integer size) {
        int resolvedPage = PageQuery.page(page);
        int resolvedSize = PageQuery.size(size);
        if (actor == null || actor.getId() == null) {
            return PageQuery.envelope(List.of(), resolvedPage, resolvedSize, 0);
        }
        var pageable = PageQuery.of(resolvedPage, resolvedSize);
        boolean seeAll = (view == null || view == UserRole.ADMIN)
                && (userAccessService.isAdmin(actor) || actor.getRole() == UserRole.ADMIN);
        boolean explicitTeamFilter = requestedTeamIds != null && !requestedTeamIds.isEmpty();
        if (seeAll && !explicitTeamFilter) {
            return PageQuery.envelope(
                    cupRepository.findListItems(pageable),
                    resolvedPage,
                    resolvedSize,
                    cupRepository.countListItems());
        }
        if (seeAll) {
            Set<UUID> teamIds = requestedTeamIds.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (teamIds.isEmpty()) {
                return PageQuery.envelope(List.of(), resolvedPage, resolvedSize, 0);
            }
            return PageQuery.envelope(
                    cupRepository.findListItemsContainingTeams(teamIds, pageable),
                    resolvedPage,
                    resolvedSize,
                    cupRepository.countListItemsContainingTeams(teamIds));
        }
        if (view == UserRole.PARENT && (requestedTeamIds == null || requestedTeamIds.isEmpty())) {
            return PageQuery.envelope(List.of(), resolvedPage, resolvedSize, 0);
        }
        Set<UUID> accessible = view == null
                ? userAccessService.getAccessibleTeamIds(actor)
                : userAccessService.getAccessibleTeamIds(actor, view);
        if (accessible == null) {
            accessible = Set.of();
        }
        Set<UUID> teamIds = requestedTeamIds == null || requestedTeamIds.isEmpty()
                ? accessible
                : requestedTeamIds.stream().filter(accessible::contains).collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestedTeamIds != null && !requestedTeamIds.isEmpty() && teamIds.isEmpty()) {
            return PageQuery.envelope(List.of(), resolvedPage, resolvedSize, 0);
        }
        if (teamIds.isEmpty()) {
            return PageQuery.envelope(
                    cupRepository.findListItemsCreatedBy(actor.getId(), pageable),
                    resolvedPage,
                    resolvedSize,
                    cupRepository.countListItemsCreatedBy(actor.getId()));
        }
        if (explicitTeamFilter) {
            return PageQuery.envelope(
                    cupRepository.findListItemsContainingTeams(teamIds, pageable),
                    resolvedPage,
                    resolvedSize,
                    cupRepository.countListItemsContainingTeams(teamIds));
        }
        return PageQuery.envelope(
                cupRepository.findListItemsForTeams(actor.getId(), teamIds, pageable),
                resolvedPage,
                resolvedSize,
                cupRepository.countListItemsForTeams(actor.getId(), teamIds));
    }

    public void requireCupViewer(UUID cupId, User actor) {
        requireCupViewer(cupId, actor, null);
    }

    public void requireCupViewer(UUID cupId, User actor, UserRole view) {
        Cup cup = cupRepository.findById(cupId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cup.not_found",
                        new Object[]{cupId},
                        "Cup not found: " + cupId
                ));
        requireCupViewer(cup, actor, view);
    }

    private void requireCupViewer(Cup cup, User actor) {
        requireCupViewer(cup, actor, null);
    }

    private void requireCupViewer(Cup cup, User actor, UserRole view) {
        if (canViewCup(cup, actor, view)) {
            return;
        }
        throw new ForbiddenException(
                "error.auth.forbidden",
                new Object[0],
                "Access denied to cup."
        );
    }

    private boolean canViewCup(Cup cup, User actor, UserRole view) {
        if (actor == null || actor.getId() == null) {
            return false;
        }
        if ((view == null || view == UserRole.ADMIN)
                && (userAccessService.isAdmin(actor) || actor.getRole() == UserRole.ADMIN)) {
            return true;
        }
        if (cup.getCreatedBy() != null && actor.getId().equals(cup.getCreatedBy().getId())) {
            return true;
        }
        Set<UUID> accessibleTeams = view == null
                ? userAccessService.getAccessibleTeamIds(actor)
                : userAccessService.getAccessibleTeamIds(actor, view);
        if (accessibleTeams.isEmpty() || cup.getTeams() == null) {
            return false;
        }
        return cup.getTeams().stream()
                .map(CupTeam::getRealTeam)
                .filter(Objects::nonNull)
                .map(Team::getId)
                .anyMatch(accessibleTeams::contains);
    }

    private void requireTeamsBelongToCup(UUID cupId, CupTeam homeTeam, CupTeam awayTeam) {
        UUID homeCupId = homeTeam.getCup() != null ? homeTeam.getCup().getId() : null;
        UUID awayCupId = awayTeam.getCup() != null ? awayTeam.getCup().getId() : null;
        if (!cupId.equals(homeCupId) || !cupId.equals(awayCupId)) {
            throw new BadRequestException(
                    "error.validation.invalid_request",
                    new Object[]{},
                    "Teams must belong to the same cup"
            );
        }
    }

    private void requireCupManager(Cup cup, User actor) {
        if (actor == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required.");
        }
        if (userAccessService.isAdmin(actor) || actor.getRole() == UserRole.ADMIN) {
            return;
        }
        UUID creatorId = cup.getCreatedBy() != null ? cup.getCreatedBy().getId() : null;
        if (creatorId != null && creatorId.equals(actor.getId())) {
            return;
        }
        throw new ForbiddenException(
                "error.auth.forbidden",
                new Object[0],
                "Only cup creator or admin can modify this cup"
        );
    }

    // ===== Mapping utilities =====

    private CupResponse mapCupToResponse(Cup cup, User viewer) {
        User createdBy = cup.getCreatedBy();
        UserResponse createdByResponse = createdBy == null ? null : new UserResponse(
                createdBy.getId(),
                null,
                createdBy.getFirstName(),
                createdBy.getLastName(),
                createdBy.getRole(),
                createdBy.getCreatedAt(),
                null
        );

        Set<CupTeamResponse> teamResponses = cup.getTeams().stream()
                .map(this::mapCupTeamToResponse)
                .collect(Collectors.toSet());

        Set<CupMatchResponse> matchResponses = cup.getMatches().stream()
                .map(match -> mapCupMatchToResponse(match, viewer))
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
            var realTeam = team.getRealTeam();
            var club = realTeam.getClub();
            realTeamResponse = new TeamResponse(
                    realTeam.getId(),
                    realTeam.getName(),
                    realTeam.getShortName(),
                    realTeam.getLogoUrl(),
                    club != null ? club.getId() : null,
                    club != null ? club.getName() : null,
                    null,
                    null,
                    null,
                    null
            );
        }

        return new CupTeamResponse(
                team.getId(),
                team.getName(),
                realTeamResponse,
                team.getCreatedAt()
        );
    }

    private CupMatchResponse mapCupMatchToResponse(CupMatch match, User viewer) {
        CupTeamResponse homeTeamResponse = mapCupTeamToResponse(match.getHomeTeam());
        CupTeamResponse awayTeamResponse = mapCupTeamToResponse(match.getAwayTeam());

        UUID realMatchId = match.getRealMatch() != null ? match.getRealMatch().getId() : null;
        if (realMatchId != null && viewer != null
                && !userAccessService.isAdmin(viewer)
                && !userAccessService.canAccessMatch(viewer, realMatchId)) {
            realMatchId = null;
        }

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




