package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.ClubAdminMembership;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.requests.CreateClubRequest;
import com.example.footballanalysis.model.requests.CreateTeamRequest;
import com.example.footballanalysis.model.requests.UpdateClubRequest;
import com.example.footballanalysis.model.responses.ClubResponse;
import com.example.footballanalysis.model.responses.TeamResponse;
import com.example.footballanalysis.model.responses.UserResponse;
import com.example.footballanalysis.repository.ClubAdminMembershipRepository;
import com.example.footballanalysis.repository.ClubRepository;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClubService {

    private final ClubRepository clubRepository;
    private final TeamRepository teamRepository;
    private final TeamService teamService;
    private final UserAccessService userAccessService;
    private final AuditEventService auditEventService;
    private final ClubAdminMembershipRepository clubAdminMembershipRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ClubResponse> listClubs(Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        if (userAccessService.isAdmin(actor)) {
            return clubRepository.findAll().stream().map(club -> toResponse(club, actor)).toList();
        }
        Map<UUID, Club> byId = new LinkedHashMap<>();
        if (actor.getId() != null) {
            for (ClubAdminMembership membership : clubAdminMembershipRepository.findByUser_Id(actor.getId())) {
                Club club = membership.getClub();
                if (club != null && club.getId() != null) {
                    byId.put(club.getId(), club);
                }
            }
        }
        for (UUID teamId : userAccessService.getAccessibleTeamIds(actor)) {
            teamRepository.findById(teamId).ifPresent(team -> {
                Club club = team.getClub();
                if (club != null && club.getId() != null) {
                    byId.putIfAbsent(club.getId(), club);
                }
            });
        }
        return byId.values().stream().map(club -> toResponse(club, actor)).toList();
    }

    @Transactional(readOnly = true)
    public ClubResponse getClub(UUID id, Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        Club club = clubRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "error.club.not_found", new Object[]{id}, "Club not found: " + id));
        userAccessService.requireClubAccess(actor, id);
        return toResponse(club, actor);
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> listClubTeams(UUID clubId, Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        if (!clubRepository.existsById(clubId)) {
            throw new NotFoundException(
                    "error.club.not_found", new Object[]{clubId}, "Club not found: " + clubId);
        }
        if (userAccessService.isAdmin(actor) || userAccessService.isClubAdmin(actor, clubId)) {
            return teamRepository.findByClub_Id(clubId).stream()
                    .map(teamService::describeTeam)
                    .toList();
        }
        if (!userAccessService.hasClubAccess(actor, clubId)) {
            throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied to club.");
        }
        Set<UUID> allowed = userAccessService.getAccessibleTeamIds(actor);
        return teamRepository.findByClub_Id(clubId).stream()
                .filter(team -> allowed.contains(team.getId()))
                .map(team -> teamService.getTeam(team.getId(), actor))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listClubUsers(UUID clubId, Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        if (!clubRepository.existsById(clubId)) {
            throw new NotFoundException(
                    "error.club.not_found", new Object[]{clubId}, "Club not found: " + clubId);
        }
        userAccessService.requireClubAdmin(actor, clubId);
        return userRepository.findUsersInClub(clubId).stream()
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getEmail(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getRole(),
                        user.getCreatedAt(),
                        null))
                .toList();
    }

    @Transactional
    public ClubResponse createClub(CreateClubRequest request, Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        if (!userAccessService.isAdmin(actor)) {
            throw new ForbiddenException("error.auth.forbidden", new Object[0], "Only a platform admin can create a club.");
        }
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isBlank()) {
            throw new BadRequestException("validation.club.name.required", new Object[0], "Club name is required.");
        }
        Club club = new Club();
        club.setName(name);
        club.setShortName(request.shortName());
        club.setLogoUrl(request.logoUrl());
        club.setCreatedByUserId(actor.getId());
        Club saved = clubRepository.save(club);
        auditEventService.record(
                "CLUB_CREATED",
                actor.getId(),
                actor.getRole() != null ? actor.getRole().name() : null,
                "CLUB",
                saved.getId().toString(),
                "name=" + saved.getName());
        log.atInfo()
                .setMessage("Created club {}")
                .addArgument(saved.getId())
                .addKeyValue("clubId", saved.getId())
                .addKeyValue("clubName", saved.getName())
                .log();
        return toResponse(saved, actor);
    }

    @Transactional
    public TeamResponse createTeamInClub(UUID clubId, CreateTeamRequest request, Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        if (!clubRepository.existsById(clubId)) {
            throw new NotFoundException(
                    "error.club.not_found", new Object[]{clubId}, "Club not found: " + clubId);
        }
        userAccessService.requireClubAdmin(actor, clubId);
        CreateTeamRequest forced = new CreateTeamRequest(
                request.name(),
                request.shortName(),
                request.logoUrl(),
                clubId);
        return teamService.createTeam(forced, actor);
    }

    @Transactional
    public ClubResponse updateClub(UUID id, UpdateClubRequest request, Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        Club club = clubRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "error.club.not_found", new Object[]{id}, "Club not found: " + id));
        userAccessService.requireClubAdmin(actor, id);
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isBlank()) {
            throw new BadRequestException("validation.club.name.required", new Object[0], "Club name is required.");
        }
        club.setName(name);
        club.setShortName(request.shortName());
        club.setLogoUrl(request.logoUrl());
        Club saved = clubRepository.save(club);
        auditEventService.record(
                "CLUB_UPDATED",
                actor.getId(),
                actor.getRole() != null ? actor.getRole().name() : null,
                "CLUB",
                saved.getId().toString(),
                "name=" + saved.getName());
        return toResponse(saved, actor);
    }

    @Transactional
    public void deleteClub(UUID id, Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        Club club = clubRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "error.club.not_found", new Object[]{id}, "Club not found: " + id));
        userAccessService.requireClubAdmin(actor, id);
        var teams = teamRepository.findByClub_Id(id);
        if (!teams.isEmpty()) {
            throw new ConflictException(
                    "error.club.has_teams",
                    new Object[]{teams.size()},
                    "Club still has teams: " + teams.size());
        }
        clubRepository.delete(club);
        auditEventService.record(
                "CLUB_DELETED",
                actor.getId(),
                actor.getRole() != null ? actor.getRole().name() : null,
                "CLUB",
                id.toString(),
                "name=" + club.getName());
        log.atInfo()
                .setMessage("Deleted club {}")
                .addArgument(id)
                .addKeyValue("clubId", id)
                .log();
    }

    private ClubResponse toResponse(Club club, User actor) {
        return new ClubResponse(
                club.getId(),
                club.getName(),
                club.getShortName(),
                club.getLogoUrl(),
                club.getCreatedAt(),
                userAccessService.isClubAdmin(actor, club.getId()));
    }
}
