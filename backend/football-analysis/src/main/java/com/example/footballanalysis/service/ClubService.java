package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.CreateClubRequest;
import com.example.footballanalysis.model.requests.UpdateClubRequest;
import com.example.footballanalysis.model.responses.ClubResponse;
import com.example.footballanalysis.model.responses.TeamResponse;
import com.example.footballanalysis.repository.ClubRepository;
import com.example.footballanalysis.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Transactional(readOnly = true)
    public List<ClubResponse> listClubs(Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        if (actor.getRole() == UserRole.ADMIN) {
            return clubRepository.findAll().stream().map(this::toResponse).toList();
        }
        Map<UUID, ClubResponse> byId = new LinkedHashMap<>();
        for (Club club : clubRepository.findByCreatedByUserId(actor.getId())) {
            byId.put(club.getId(), toResponse(club));
        }
        for (TeamResponse team : teamService.getMyTeams(jwt)) {
            if (team.clubId() == null) {
                continue;
            }
            byId.putIfAbsent(team.clubId(), new ClubResponse(
                    team.clubId(),
                    team.clubName(),
                    null,
                    null,
                    null));
        }
        return List.copyOf(byId.values());
    }

    @Transactional(readOnly = true)
    public ClubResponse getClub(UUID id, Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        Club club = clubRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "error.club.not_found", new Object[]{id}, "Club not found: " + id));
        userAccessService.requireClubAccess(actor, id);
        return toResponse(club);
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> listClubTeams(UUID clubId, Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        if (!clubRepository.existsById(clubId)) {
            throw new NotFoundException(
                    "error.club.not_found", new Object[]{clubId}, "Club not found: " + clubId);
        }
        userAccessService.requireClubAccess(actor, clubId);
        return teamRepository.findByClub_Id(clubId).stream()
                .map(team -> teamService.getTeam(team.getId(), actor))
                .toList();
    }

    @Transactional
    public ClubResponse createClub(CreateClubRequest request, Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
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
        return toResponse(saved);
    }

    @Transactional
    public ClubResponse updateClub(UUID id, UpdateClubRequest request, Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        Club club = clubRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "error.club.not_found", new Object[]{id}, "Club not found: " + id));
        userAccessService.requireClubAccess(actor, id);
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
        return toResponse(saved);
    }

    @Transactional
    public void deleteClub(UUID id, Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        Club club = clubRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "error.club.not_found", new Object[]{id}, "Club not found: " + id));
        userAccessService.requireClubAccess(actor, id);
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

    private ClubResponse toResponse(Club club) {
        return new ClubResponse(
                club.getId(),
                club.getName(),
                club.getShortName(),
                club.getLogoUrl(),
                club.getCreatedAt());
    }
}
