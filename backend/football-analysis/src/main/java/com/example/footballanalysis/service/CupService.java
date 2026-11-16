package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.*;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.CreateCupRequest;
import com.example.footballanalysis.model.requests.CupMatchScheduleRequest;
import com.example.footballanalysis.model.requests.CupTeamCreateRequest;
import com.example.footballanalysis.model.requests.UpdateCupRequest;
import com.example.footballanalysis.model.requests.UpdateMatchScoreRequest;
import com.example.footballanalysis.model.responses.*;
import com.example.footballanalysis.paging.PageQuery;
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

/**
 * Cups: teams, matches, results and the link to analysed matches.
 * Who may do what is decided by {@link CupAccessPolicy}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CupService {

    /** Matches by time; equal times by creation, then id, so the order never changes between loads (CU9). */
    private static final Comparator<CupMatch> MATCH_ORDER = Comparator
            .comparing(CupMatch::getScheduledAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(CupMatch::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(CupMatch::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Comparator<CupTeam> TEAM_ORDER = Comparator
            .comparing(CupTeam::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(CupTeam::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final CupRepository cupRepository;
    private final CupTeamRepository cupTeamRepository;
    private final CupMatchRepository cupMatchRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final AuditEventService auditEventService;
    private final UserAccessService userAccessService;
    private final CupAccessPolicy access;
    private final CupStandingsService standingsService;

    // ===== Cup =====

    @Transactional
    public CupResponse createCup(CreateCupRequest request, User creator) {
        access.requireCreator(creator);
        Cup cup = new Cup();
        cup.setName(requireName(request.name(), "Cup name cannot be empty"));
        cup.setCreatedBy(creator);
        Cup saved = cupRepository.save(cup);

        audit("CUP_CREATED", creator, "CUP", saved.getId(), saved.getId(),
                String.format("Cup '%s' created", saved.getName()));
        return mapCup(saved, creator, null);
    }

    @Transactional(readOnly = true)
    public CupResponse getCup(UUID cupId, User actor) {
        return getCup(cupId, actor, null);
    }

    @Transactional(readOnly = true)
    public CupResponse getCup(UUID cupId, User actor, UserRole view) {
        return mapCup(loadViewableCup(cupId, actor, view), actor, view);
    }

    /** 2.6: rename. Manager only. */
    @Transactional
    public CupResponse renameCup(UUID cupId, UpdateCupRequest request, User actor) {
        Cup cup = access.requireCup(cupId);
        access.requireManager(cup, actor);
        String oldName = cup.getName();
        cup.setName(requireName(request.name(), "Cup name cannot be empty"));
        Cup saved = cupRepository.save(cup);

        audit("CUP_RENAMED", actor, "CUP", cupId, cupId,
                String.format("Cup '%s' renamed to '%s'", oldName, saved.getName()));
        return mapCup(saved, actor, null);
    }

    /**
     * 2.6: delete with its teams and matches. A cup with a played match is deleted by an admin only (D7).
     * Team events keep existing: {@code team_events.cup_match_id} is {@code ON DELETE SET NULL}.
     */
    @Transactional
    public void deleteCup(UUID cupId, User actor) {
        Cup cup = access.requireCup(cupId);
        access.requireManager(cup, actor);
        if (!userAccessService.isAdmin(actor) && cupMatchRepository.existsByCup_IdAndPlayedTrue(cupId)) {
            throw new ConflictException("error.cup.has_played_matches", new Object[0],
                    "A cup with played matches can only be deleted by an admin.");
        }
        // Matches first: they reference the cup teams.
        cupMatchRepository.deleteAll(cup.getMatches());
        cupMatchRepository.flush();
        cup.getMatches().clear();
        cupRepository.delete(cup);

        audit("CUP_DELETED", actor, "CUP", cupId, cupId, String.format("Cup '%s' deleted", cup.getName()));
    }

    // ===== Teams =====

    @Transactional(readOnly = true)
    public List<CupTeamResponse> listTeams(UUID cupId, User actor, UserRole view) {
        Cup cup = loadViewableCup(cupId, actor, view);
        Set<UUID> own = access.accessibleTeamIds(actor, view);
        return cup.getTeams().stream().sorted(TEAM_ORDER).map(team -> mapTeam(team, own)).toList();
    }

    @Transactional
    public CupTeamResponse addTeamToCup(UUID cupId, CupTeamCreateRequest request, User actor) {
        Cup cup = access.requireCup(cupId);
        access.requireManager(cup, actor);
        String name = requireName(request.name(), "Team name cannot be empty");
        Team realTeam = resolveRealTeam(request.realTeamId(), actor);
        requireUniqueTeam(cupId, name, realTeam, null);

        CupTeam team = new CupTeam();
        team.setCup(cup);
        team.setName(name);
        team.setRealTeam(realTeam);
        CupTeam saved = cupTeamRepository.save(team);

        audit("TEAM_ADDED", actor, "CUP_TEAM", saved.getId(), cupId,
                String.format("Team '%s' added to cup '%s'", name, cup.getName()));
        return mapTeam(saved, access.accessibleTeamIds(actor, null));
    }

    /** Rename or relink a cup team. {@code realTeamId == null} removes the link. */
    @Transactional
    public CupTeamResponse updateTeam(UUID cupId, UUID teamId, CupTeamCreateRequest request, User actor) {
        Cup cup = access.requireCup(cupId);
        access.requireManager(cup, actor);
        CupTeam team = access.requireCupTeam(cupId, teamId);
        String name = requireName(request.name(), "Team name cannot be empty");

        UUID currentRealTeamId = team.getRealTeam() != null ? team.getRealTeam().getId() : null;
        // Keeping the current link needs no check (CU3 only guards a new link).
        Team realTeam = Objects.equals(request.realTeamId(), currentRealTeamId)
                ? team.getRealTeam()
                : resolveRealTeam(request.realTeamId(), actor);
        requireUniqueTeam(cupId, name, realTeam, teamId);

        team.setName(name);
        team.setRealTeam(realTeam);
        CupTeam saved = cupTeamRepository.save(team);

        audit("CUP_TEAM_UPDATED", actor, "CUP_TEAM", teamId, cupId,
                String.format("Team '%s' updated in cup '%s'", name, cup.getName()));
        return mapTeam(saved, access.accessibleTeamIds(actor, null));
    }

    @Transactional
    public void deleteTeam(UUID cupId, UUID teamId, User actor) {
        Cup cup = access.requireCup(cupId);
        access.requireManager(cup, actor);
        CupTeam team = access.requireCupTeam(cupId, teamId);

        long referencedMatches = cupMatchRepository.countByCupIdAndTeamId(cupId, teamId);
        if (referencedMatches > 0) {
            throw new BadRequestException("error.cupteam.referenced_by_matches", new Object[]{referencedMatches},
                    "Cannot delete team because it is referenced by " + referencedMatches + " match(es)");
        }
        cupTeamRepository.delete(team);

        audit("CUP_TEAM_DELETED", actor, "CUP_TEAM", teamId, cupId,
                String.format("Team '%s' deleted from cup '%s'", team.getName(), cup.getName()));
    }

    // ===== Matches =====

    @Transactional(readOnly = true)
    public List<CupMatchResponse> listMatches(UUID cupId, User actor, UserRole view) {
        Cup cup = loadViewableCup(cupId, actor, view);
        Set<UUID> own = access.accessibleTeamIds(actor, view);
        Set<UUID> visible = visibleRealMatchIds(actor, cup.getMatches());
        return cup.getMatches().stream().sorted(MATCH_ORDER).map(match -> mapMatch(match, visible, own)).toList();
    }

    @Transactional
    public CupMatchResponse scheduleMatch(UUID cupId, CupMatchScheduleRequest request, User actor) {
        Cup cup = access.requireCup(cupId);
        access.requireManager(cup, actor);
        CupTeam home = access.requireCupTeam(cupId, request.homeTeamId());
        CupTeam away = access.requireCupTeam(cupId, request.awayTeamId());
        requireDifferentTeams(home, away);

        CupMatch match = new CupMatch();
        match.setCup(cup);
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setScheduledAt(request.scheduledAt());
        match.setPlayed(false);
        match.setLeg(freeLeg(cupId, home, away, null));
        CupMatch saved = cupMatchRepository.save(match);

        audit("MATCH_SCHEDULED", actor, "CUP_MATCH", saved.getId(), cupId,
                String.format("Match scheduled: %s vs %s in cup '%s'", home.getName(), away.getName(), cup.getName()));
        return mapMatch(saved, actor);
    }

    @Transactional
    public CupMatchResponse updateMatch(UUID cupId, UUID matchId, CupMatchScheduleRequest request, User actor) {
        Cup cup = access.requireCup(cupId);
        access.requireManager(cup, actor);
        CupMatch match = access.requireCupMatch(cupId, matchId);
        CupTeam home = access.requireCupTeam(cupId, request.homeTeamId());
        CupTeam away = access.requireCupTeam(cupId, request.awayTeamId());
        requireDifferentTeams(home, away);

        // Same pairing (in either direction): the match keeps its leg. A new pairing gets a free one.
        if (!samePairing(match, home, away)) {
            match.setLeg(freeLeg(cupId, home, away, matchId));
        }
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setScheduledAt(request.scheduledAt());
        CupMatch saved = cupMatchRepository.save(match);

        audit("CUP_MATCH_UPDATED", actor, "CUP_MATCH", matchId, cupId,
                String.format("Match updated in cup '%s'", cup.getName()));
        return mapMatch(saved, actor);
    }

    @Transactional
    public void deleteMatch(UUID cupId, UUID matchId, User actor) {
        Cup cup = access.requireCup(cupId);
        access.requireManager(cup, actor);
        CupMatch match = access.requireCupMatch(cupId, matchId);
        cupMatchRepository.delete(match);

        audit("CUP_MATCH_DELETED", actor, "CUP_MATCH", matchId, cupId,
                String.format("Match deleted from cup '%s'", cup.getName()));
    }

    @Transactional
    public CupMatchResponse recordScore(UUID cupId, UUID matchId, UpdateMatchScoreRequest request, User actor) {
        Cup cup = access.requireCup(cupId);
        access.requireManager(cup, actor);
        CupMatch match = access.requireCupMatch(cupId, matchId);
        if (request.homeScore() < 0 || request.awayScore() < 0) {
            throw new BadRequestException("error.validation.negative_score", new Object[]{}, "Scores cannot be negative");
        }
        match.setHomeScore(request.homeScore());
        match.setAwayScore(request.awayScore());
        match.setPlayed(true);
        CupMatch saved = cupMatchRepository.save(match);

        audit("CUP_MATCH_RESULT_UPDATED", actor, "CUP_MATCH", matchId, cupId,
                String.format("Score recorded: %s %d-%d %s in cup '%s'", match.getHomeTeam().getName(),
                        request.homeScore(), request.awayScore(), match.getAwayTeam().getName(), cup.getName()));
        return mapMatch(saved, actor);
    }

    /**
     * 2.5: the match becomes "not played" again. On a match with no result nothing changes (D2):
     * a second manager on an old screen gets no error.
     */
    @Transactional
    public void clearScore(UUID cupId, UUID matchId, User actor) {
        Cup cup = access.requireCup(cupId);
        access.requireManager(cup, actor);
        CupMatch match = access.requireCupMatch(cupId, matchId);
        if (!match.isPlayed()) {
            return;
        }
        match.setPlayed(false);
        match.setHomeScore(null);
        match.setAwayScore(null);
        cupMatchRepository.save(match);

        audit("CUP_MATCH_SCORE_CLEARED", actor, "CUP_MATCH", matchId, cupId,
                String.format("Score cleared: %s vs %s in cup '%s'", match.getHomeTeam().getName(),
                        match.getAwayTeam().getName(), cup.getName()));
    }

    // ===== Analysed match link (2.1, CU5) =====

    /**
     * Links an analysed match. Manager only, the caller must see the match, and the match must be played
     * by one of the cup match's real teams.
     */
    @Transactional
    public CupMatchResponse linkRealMatch(UUID cupId, UUID matchId, UUID realMatchId, User actor) {
        Cup cup = access.requireCup(cupId);
        access.requireManager(cup, actor);
        CupMatch match = access.requireCupMatch(cupId, matchId);

        Set<UUID> cupRealTeams = realTeamIds(match);
        if (cupRealTeams.isEmpty()) {
            throw new BadRequestException("error.cup.match.no_real_team", new Object[0],
                    "Neither side of this cup match is linked to a real team.");
        }
        Match realMatch = matchRepository.findById(realMatchId)
                .orElseThrow(() -> new NotFoundException("error.match.not_found", new Object[]{realMatchId},
                        "Match not found: " + realMatchId));
        userAccessService.requireMatchAccess(actor, realMatchId);
        boolean sameTeam = (realMatch.getHomeTeam() != null && cupRealTeams.contains(realMatch.getHomeTeam().getId()))
                || (realMatch.getAwayTeam() != null && cupRealTeams.contains(realMatch.getAwayTeam().getId()));
        if (!sameTeam) {
            throw new BadRequestException("error.cup.match.real_match_team_mismatch", new Object[0],
                    "The analysed match was not played by a team of this cup match.");
        }

        match.setRealMatch(realMatch);
        CupMatch saved = cupMatchRepository.save(match);

        audit("CUP_MATCH_LINKED", actor, "CUP_MATCH", matchId, cupId,
                String.format("Real match linked to cup match in cup '%s'", cup.getName()));
        return mapMatch(saved, actor);
    }

    @Transactional
    public CupMatchResponse unlinkRealMatch(UUID cupId, UUID matchId, User actor) {
        Cup cup = access.requireCup(cupId);
        access.requireManager(cup, actor);
        CupMatch match = access.requireCupMatch(cupId, matchId);
        match.setRealMatch(null);
        CupMatch saved = cupMatchRepository.save(match);

        audit("CUP_MATCH_UNLINKED", actor, "CUP_MATCH", matchId, cupId,
                String.format("Real match unlinked in cup '%s'", cup.getName()));
        return mapMatch(saved, actor);
    }

    /** 2.3: the cups an analysed match belongs to, only those the caller can see. */
    @Transactional(readOnly = true)
    public List<MatchCupLinkResponse> listCupLinksForMatch(UUID realMatchId, User actor, UserRole view) {
        userAccessService.requireMatchAccess(actor, realMatchId);
        return cupMatchRepository.findByRealMatchIdWithCup(realMatchId).stream()
                .filter(cupMatch -> access.canView(cupMatch.getCup(), actor, view))
                .map(cupMatch -> new MatchCupLinkResponse(cupMatch.getCup().getId(), cupMatch.getCup().getName(), cupMatch.getId()))
                .toList();
    }

    // ===== Standings =====

    @Transactional(readOnly = true)
    public List<CupStandingsRowResponse> getStandings(UUID cupId, User actor, UserRole view) {
        Cup cup = loadViewableCup(cupId, actor, view);
        Set<UUID> accessible = access.accessibleTeamIds(actor, view);
        Set<UUID> ownCupTeamIds = cup.getTeams().stream()
                .filter(team -> isOwn(team, accessible))
                .map(CupTeam::getId)
                .collect(Collectors.toSet());
        return standingsService.calculateStandings(cupId, cup.getTeams(), ownCupTeamIds);
    }

    // ===== List =====

    @Transactional(readOnly = true)
    public PageResponse<CupListItem> listAllCups(User actor, UserRole view, List<UUID> requestedTeamIds, Integer page, Integer size) {
        PageResponse<CupListItem> result = findCupPage(actor, view, requestedTeamIds, page, size);
        if (result.items().isEmpty()) {
            return result;
        }
        List<CupListItem> items = result.items().stream()
                .map(item -> item.withCanManage(access.canManage(item.createdById(), actor, view)))
                .toList();
        return new PageResponse<>(items, result.page(), result.size(), result.total(), result.totalPages());
    }

    private PageResponse<CupListItem> findCupPage(User actor, UserRole view, List<UUID> requestedTeamIds, Integer page, Integer size) {
        int resolvedPage = PageQuery.page(page);
        int resolvedSize = PageQuery.size(size);
        if (actor == null || actor.getId() == null) {
            return PageQuery.envelope(List.of(), resolvedPage, resolvedSize, 0);
        }
        var pageable = PageQuery.of(resolvedPage, resolvedSize);
        boolean seeAll = access.isAdminView(actor, view);
        boolean explicitTeamFilter = requestedTeamIds != null && !requestedTeamIds.isEmpty();
        if (seeAll && !explicitTeamFilter) {
            return PageQuery.envelope(cupRepository.findListItems(pageable), resolvedPage, resolvedSize,
                    cupRepository.countListItems());
        }
        if (seeAll) {
            Set<UUID> teamIds = requestedTeamIds.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (teamIds.isEmpty()) {
                return PageQuery.envelope(List.of(), resolvedPage, resolvedSize, 0);
            }
            return PageQuery.envelope(cupRepository.findListItemsContainingTeams(teamIds, pageable), resolvedPage,
                    resolvedSize, cupRepository.countListItemsContainingTeams(teamIds));
        }
        if (view == UserRole.PARENT && !explicitTeamFilter) {
            return PageQuery.envelope(List.of(), resolvedPage, resolvedSize, 0);
        }
        Set<UUID> accessible = access.accessibleTeamIds(actor, view);
        Set<UUID> teamIds = explicitTeamFilter
                ? requestedTeamIds.stream().filter(accessible::contains).collect(Collectors.toCollection(LinkedHashSet::new))
                : accessible;
        if (explicitTeamFilter && teamIds.isEmpty()) {
            return PageQuery.envelope(List.of(), resolvedPage, resolvedSize, 0);
        }
        if (teamIds.isEmpty()) {
            return PageQuery.envelope(cupRepository.findListItemsCreatedBy(actor.getId(), pageable), resolvedPage,
                    resolvedSize, cupRepository.countListItemsCreatedBy(actor.getId()));
        }
        if (explicitTeamFilter) {
            return PageQuery.envelope(cupRepository.findListItemsContainingTeams(teamIds, pageable), resolvedPage,
                    resolvedSize, cupRepository.countListItemsContainingTeams(teamIds));
        }
        return PageQuery.envelope(cupRepository.findListItemsForTeams(actor.getId(), teamIds, pageable), resolvedPage,
                resolvedSize, cupRepository.countListItemsForTeams(actor.getId(), teamIds));
    }

    // ===== Checks =====

    @Transactional(readOnly = true)
    public void requireCupViewer(UUID cupId, User actor, UserRole view) {
        loadViewableCup(cupId, actor, view);
    }

    private Cup loadViewableCup(UUID cupId, User actor, UserRole view) {
        Cup cup = access.requireCup(cupId);
        access.requireViewer(cup, actor, view);
        return cup;
    }

    /** CU8: trimmed, never blank. */
    private static String requireName(String raw, String fallback) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new BadRequestException("error.validation.invalid_request", new Object[]{"name"}, fallback);
        }
        return name;
    }

    /** CU3: the real team must exist and the caller must run it. */
    private Team resolveRealTeam(UUID realTeamId, User actor) {
        if (realTeamId == null) {
            return null;
        }
        Team realTeam = teamRepository.findById(realTeamId)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{realTeamId},
                        "Team not found: " + realTeamId));
        access.requireLinkableRealTeam(actor, realTeam);
        return realTeam;
    }

    /** CU12: one name and one real team only once per cup. {@code selfId} is the team being edited. */
    private void requireUniqueTeam(UUID cupId, String name, Team realTeam, UUID selfId) {
        boolean nameTaken = cupTeamRepository.findSameName(cupId, name).stream()
                .anyMatch(other -> !other.getId().equals(selfId));
        if (nameTaken) {
            throw new ConflictException("error.cup.team.name_taken", new Object[]{name},
                    "This team is already in the cup: " + name);
        }
        if (realTeam != null) {
            boolean realTeamTaken = cupTeamRepository.findByCup_IdAndRealTeam_Id(cupId, realTeam.getId()).stream()
                    .anyMatch(other -> !other.getId().equals(selfId));
            if (realTeamTaken) {
                throw new ConflictException("error.cup.team.real_team_taken", new Object[]{realTeam.getName()},
                        "This team is already in the cup: " + realTeam.getName());
            }
        }
    }

    private static void requireDifferentTeams(CupTeam home, CupTeam away) {
        if (home.getId().equals(away.getId())) {
            throw new BadRequestException("error.validation.invalid_request", new Object[]{"teams"},
                    "Home team and away team cannot be the same");
        }
    }

    /**
     * CU7 + 2.7: A–B and B–A are the same pairing. The first match is leg 1, the second is the return match
     * (leg 2), a third is refused. {@code selfId} is the match being edited.
     */
    private short freeLeg(UUID cupId, CupTeam home, CupTeam away, UUID selfId) {
        Set<Short> usedLegs = cupMatchRepository.findPairing(cupId, home.getId(), away.getId()).stream()
                .filter(other -> !other.getId().equals(selfId))
                .map(CupMatch::getLeg)
                .collect(Collectors.toSet());
        if (!usedLegs.contains((short) 1)) {
            return 1;
        }
        if (!usedLegs.contains((short) 2)) {
            return 2;
        }
        throw new ConflictException("error.cup.match.duplicate_pairing", new Object[]{home.getName(), away.getName()},
                "These teams already play each other twice in this cup.");
    }

    private static boolean samePairing(CupMatch match, CupTeam home, CupTeam away) {
        Set<UUID> before = Set.of(match.getHomeTeam().getId(), match.getAwayTeam().getId());
        return before.equals(Set.of(home.getId(), away.getId()));
    }

    private static Set<UUID> realTeamIds(CupMatch match) {
        Set<UUID> ids = new HashSet<>();
        if (match.getHomeTeam().getRealTeam() != null) {
            ids.add(match.getHomeTeam().getRealTeam().getId());
        }
        if (match.getAwayTeam().getRealTeam() != null) {
            ids.add(match.getAwayTeam().getRealTeam().getId());
        }
        return ids;
    }

    private static boolean isOwn(CupTeam team, Set<UUID> accessibleTeamIds) {
        return team.getRealTeam() != null && accessibleTeamIds.contains(team.getRealTeam().getId());
    }

    /** Writes an application log line and an audit event. */
    private void audit(String eventType, User actor, String entityType, UUID entityId, UUID cupId, String description) {
        log.atInfo()
                .setMessage("{}: cupId={}, entityId={}")
                .addArgument(eventType)
                .addArgument(cupId)
                .addArgument(entityId)
                .addKeyValue("event_type", eventType)
                .addKeyValue("cup_id", cupId)
                .addKeyValue("entity_id", entityId)
                .addKeyValue("actor_id", actor.getId())
                .addKeyValue("trace_id", MDC.get("traceId"))
                .log();
        auditEventService.record(eventType, actor.getId(), entityType, entityId.toString(), description);
    }

    // ===== Mapping =====

    private CupResponse mapCup(Cup cup, User viewer, UserRole view) {
        User createdBy = cup.getCreatedBy();
        UserResponse createdByResponse = createdBy == null ? null : new UserResponse(
                createdBy.getId(), null, createdBy.getFirstName(), createdBy.getLastName(), createdBy.getCreatedAt(), null);

        Set<UUID> own = access.accessibleTeamIds(viewer, view);
        List<CupTeamResponse> teams = cup.getTeams().stream().sorted(TEAM_ORDER).map(team -> mapTeam(team, own)).toList();
        Set<UUID> visible = visibleRealMatchIds(viewer, cup.getMatches());
        List<CupMatchResponse> matches = cup.getMatches().stream().sorted(MATCH_ORDER)
                .map(match -> mapMatch(match, visible, own)).toList();

        return new CupResponse(cup.getId(), cup.getName(), cup.getCreatedAt(), createdByResponse, teams, matches,
                access.canManage(cup, viewer, view));
    }

    private CupTeamResponse mapTeam(CupTeam team, Set<UUID> accessibleTeamIds) {
        TeamResponse realTeamResponse = null;
        if (team.getRealTeam() != null) {
            var realTeam = team.getRealTeam();
            var club = realTeam.getClub();
            realTeamResponse = new TeamResponse(realTeam.getId(), realTeam.getName(), realTeam.getShortName(),
                    realTeam.getLogoUrl(), club != null ? club.getId() : null, club != null ? club.getName() : null,
                    null, null, null, null);
        }
        return new CupTeamResponse(team.getId(), team.getName(), realTeamResponse, team.getCreatedAt(),
                isOwn(team, accessibleTeamIds));
    }

    private CupMatchResponse mapMatch(CupMatch match, User viewer) {
        return mapMatch(match, visibleRealMatchIds(viewer, List.of(match)), access.accessibleTeamIds(viewer, null));
    }

    /** Null means no viewer (internal call): every linked match id stays visible. */
    private Set<UUID> visibleRealMatchIds(User viewer, Collection<CupMatch> matches) {
        if (viewer == null) {
            return null;
        }
        if (matches == null || matches.isEmpty()) {
            return Set.of();
        }
        List<UUID> ids = matches.stream()
                .map(CupMatch::getRealMatch)
                .filter(Objects::nonNull)
                .map(Match::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return ids.isEmpty() ? Set.of() : userAccessService.accessibleMatchIds(viewer, ids);
    }

    private CupMatchResponse mapMatch(CupMatch match, Set<UUID> visibleRealMatchIds, Set<UUID> accessibleTeamIds) {
        UUID realMatchId = match.getRealMatch() != null ? match.getRealMatch().getId() : null;
        if (realMatchId != null && visibleRealMatchIds != null && !visibleRealMatchIds.contains(realMatchId)) {
            realMatchId = null;
        }
        return new CupMatchResponse(
                match.getId(),
                mapTeam(match.getHomeTeam(), accessibleTeamIds),
                mapTeam(match.getAwayTeam(), accessibleTeamIds),
                match.getHomeScore(),
                match.getAwayScore(),
                match.isPlayed(),
                match.getScheduledAt(),
                realMatchId,
                match.getCreatedAt(),
                match.getLeg());
    }
}
