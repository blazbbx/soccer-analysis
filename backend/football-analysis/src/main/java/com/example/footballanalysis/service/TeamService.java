package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Fan;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.requests.CreateTeamRequest;
import com.example.footballanalysis.model.requests.UpdateTeamRequest;
import com.example.footballanalysis.model.responses.TeamResponse;
import com.example.footballanalysis.repository.CoachRepository;
import com.example.footballanalysis.repository.FanRepository;
import com.example.footballanalysis.repository.PlayerRepository;
import com.example.footballanalysis.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final CoachRepository coachRepository;
    private final FanRepository fanRepository;

    @Transactional(readOnly = true)
    public List<TeamResponse> getAllTeams() {
        return teamRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TeamResponse getTeam(UUID id) {
        return toResponse(teamRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{id}, "Team not found: " + id)));
    }

    @Transactional
    public TeamResponse createTeam(CreateTeamRequest request) {
        String name = request.name().trim();
        if (name.isBlank()) {
            throw new BadRequestException("validation.team.name.required", new Object[0], "Team name is required.");
        }
        if (teamRepository.existsByName(name)) {
            throw new ConflictException("error.team.name.conflict", new Object[]{name}, "A team with this name already exists: " + name);
        }

        Team team = new Team();
        team.setName(name);
        team.setShortName(request.shortName());
        team.setLogoUrl(request.logoUrl());
        return toResponse(teamRepository.save(team));
    }

    @Transactional
    public TeamResponse updateTeam(UUID id, UpdateTeamRequest request) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{id}, "Team not found: " + id));

        String name = request.name().trim();
        if (name.isBlank()) {
            throw new BadRequestException("validation.team.name.required", new Object[0], "Team name is required.");
        }
        if (teamRepository.existsByNameAndIdNot(name, id)) {
            throw new ConflictException("error.team.name.conflict", new Object[]{name}, "Another team with this name already exists: " + name);
        }

        team.setName(name);
        team.setShortName(request.shortName());
        team.setLogoUrl(request.logoUrl());
        return toResponse(teamRepository.save(team));
    }

    @Transactional
    public void deleteTeam(UUID id) {
        if (!teamRepository.existsById(id)) {
            throw new NotFoundException("error.team.not_found", new Object[]{id}, "Team not found: " + id);
        }
        teamRepository.deleteById(id);
    }

    // ── Játékos kezelés ───────────────────────────────────────────────────────

    @Transactional
    public void addPlayerToTeam(UUID teamId, UUID playerId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found: " + teamId));
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new NotFoundException("error.player.not_found", new Object[]{playerId}, "Player not found: " + playerId));

        if (team.getPlayers().contains(player)) {
            throw new ConflictException("error.team.player.already_member", new Object[0], "Player is already a member of this team.");
        }

        player.addTeam(team);
        playerRepository.save(player);
    }

    @Transactional
    public void removePlayerFromTeam(UUID teamId, UUID playerId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found: " + teamId));
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new NotFoundException("error.player.not_found", new Object[]{playerId}, "Player not found: " + playerId));

        if (!team.getPlayers().contains(player)) {
            throw new ConflictException("error.team.player.not_member", new Object[0], "Player is not a member of this team.");
        }
        player.removeTeam(team);
        playerRepository.save(player);
    }

    // ── Coach kezelés ─────────────────────────────────────────────────────────

    @Transactional
    public void addCoachToTeam(UUID teamId, UUID coachId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found: " + teamId));
        Coach coach = coachRepository.findById(coachId)
                .orElseThrow(() -> new NotFoundException("error.coach.not_found", new Object[]{coachId}, "Coach not found: " + coachId));

        if (team.getCoaches().contains(coach)) {
            throw new ConflictException("error.team.coach.already_member", new Object[0], "Coach is already a member of this team.");
        }

        coach.addTeam(team);
        coachRepository.save(coach);
    }

    @Transactional
    public void removeCoachFromTeam(UUID teamId, UUID coachId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found: " + teamId));
        Coach coach = coachRepository.findById(coachId)
                .orElseThrow(() -> new NotFoundException("error.coach.not_found", new Object[]{coachId}, "Coach not found: " + coachId));

        if (!team.getCoaches().contains(coach)) {
            throw new ConflictException("error.team.coach.not_member", new Object[0], "Coach is not a member of this team.");
        }

        coach.removeTeam(team);
        coachRepository.save(coach);
    }

    // ── Fan kezelés ─────────────────────────────────────────────────────────

    @Transactional
    public void addFanToTeam(UUID teamId, UUID fanId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found: " + teamId));
        Fan fan = fanRepository.findById(fanId)
                .orElseThrow(() -> new NotFoundException("error.fan.not_found", new Object[]{fanId}, "Fan not found: " + fanId));

        if (fan.getTeams().contains(team)) {
            throw new ConflictException("error.team.fan.already_following", new Object[0], "Fan is already following this team.");
        }

        fan.addTeam(team);
        fanRepository.save(fan);
    }

    @Transactional
    public void removeFanFromTeam(UUID teamId, UUID fanId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("error.team.not_found", new Object[]{teamId}, "Team not found: " + teamId));
        Fan fan = fanRepository.findById(fanId)
                .orElseThrow(() -> new NotFoundException("error.fan.not_found", new Object[]{fanId}, "Fan not found: " + fanId));

        if (!fan.getTeams().contains(team)) {
            throw new ConflictException("error.team.fan.not_following", new Object[0], "Fan is not following this team.");
        }

        fan.removeTeam(team);
        fanRepository.save(fan);
    }

    // ── Entitás → DTO konverzió ───────────────────────────────────────────────
    private TeamResponse toResponse(Team team) {
        List<TeamResponse.MemberInfo> players = team.getPlayers().stream()
                .map(p -> new TeamResponse.MemberInfo(p.getId(), p.getFirstName(), p.getLastName()))
                .toList();

        List<TeamResponse.MemberInfo> coaches = team.getCoaches().stream()
                .map(c -> new TeamResponse.MemberInfo(c.getId(), c.getFirstName(), c.getLastName()))
                .toList();


        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getShortName(),
                team.getLogoUrl(),
                players,
                coaches
        );
    }
}
