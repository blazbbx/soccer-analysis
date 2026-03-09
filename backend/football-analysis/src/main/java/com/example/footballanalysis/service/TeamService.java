package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Fan;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.responses.TeamResponse;
import com.example.footballanalysis.repository.CoachRepository;
import com.example.footballanalysis.repository.FanRepository;
import com.example.footballanalysis.repository.PlayerRepository;
import com.example.footballanalysis.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
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
                .orElseThrow(() -> new RuntimeException("Team not found: " + id)));
    }

    @Transactional
    public TeamResponse createTeam(Map<String, String> body) {
        Team team = new Team();
        team.setName(body.get("name"));
        team.setShortName(body.get("shortName"));
        team.setLogoUrl(body.get("logoUrl"));
        return toResponse(teamRepository.save(team));
    }

    @Transactional
    public TeamResponse updateTeam(UUID id, Map<String, String> body) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Team not found: " + id));
        team.setName(body.get("name"));
        team.setShortName(body.get("shortName"));
        team.setLogoUrl(body.get("logoUrl"));
        return toResponse(teamRepository.save(team));
    }

    @Transactional
    public void deleteTeam(UUID id) {
        teamRepository.deleteById(id);
    }

    // ── Játékos kezelés ───────────────────────────────────────────────────────

    @Transactional
    public void addPlayerToTeam(UUID teamId, UUID playerId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found: " + teamId));
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("Player not found: " + playerId));

        player.addTeam(team);
        teamRepository.save(team);
    }

    @Transactional
    public void removePlayerFromTeam(UUID teamId, UUID playerId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found: " + teamId));
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("Player not found: " + playerId));
        player.removeTeam(team);
        teamRepository.save(team);
    }

    // ── Coach kezelés ─────────────────────────────────────────────────────────

    @Transactional
    public void addCoachToTeam(UUID teamId, UUID coachId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found: " + teamId));
        Coach coach = coachRepository.findById(coachId)
                .orElseThrow(() -> new RuntimeException("Coach not found: " + coachId));

        coach.addTeam(team);
        teamRepository.save(team);
    }

    @Transactional
    public void removeCoachFromTeam(UUID teamId, UUID coachId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found: " + teamId));
        Coach coach = coachRepository.findById(coachId)
                .orElseThrow(() -> new RuntimeException("Coach not found: " + coachId));
        coach.removeTeam(team);
        teamRepository.save(team);
    }

    // ── Fan kezelés ─────────────────────────────────────────────────────────

    @Transactional
    public void addFanToTeam(UUID teamId, UUID fanId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found: " + teamId));
        Fan fan = fanRepository.findById(fanId)
                .orElseThrow(() -> new RuntimeException("Coach not found: " + fanId));

        fan.addTeam(team);
        teamRepository.save(team);
    }

    @Transactional
    public void removeFanFromTeam(UUID teamId, UUID fanId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found: " + teamId));
        Fan fan = fanRepository.findById(fanId)
                .orElseThrow(() -> new RuntimeException("Coach not found: " + fanId));
        fan.removeTeam(team);
        teamRepository.save(team);
    }

    // ── Entitás → DTO konverzió ───────────────────────────────────────────────
    private TeamResponse toResponse(Team team) {
        List<TeamResponse.MemberInfo> players = team.getPlayers().stream()
                .map(p -> new TeamResponse.MemberInfo(p.getId(), p.getFullName()))
                .toList();

        List<TeamResponse.MemberInfo> coaches = team.getCoaches().stream()
                .map(c -> new TeamResponse.MemberInfo(c.getId(), c.getFullName()))
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
