package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Fan;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.requests.CreateTeamRequest;
import com.example.footballanalysis.model.responses.TeamResponse;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.CoachRepository;
import com.example.footballanalysis.repository.FanRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.MatchSquadMemberRepository;
import com.example.footballanalysis.repository.PlayerRepository;
import com.example.footballanalysis.repository.TeamInviteRepository;
import com.example.footballanalysis.repository.TeamRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.example.footballanalysis.repository.UserRepository;
import java.time.Instant;
import java.util.Map;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeamService tesztek")
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private CoachRepository coachRepository;

    @Mock
    private FanRepository fanRepository;

    @Mock
    private TeamInviteRepository teamInviteRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private ClipRepository clipRepository;

    @Mock
    private MatchSquadMemberRepository matchSquadMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MinioObjectCleanupService minioObjectCleanupService;

    @Mock
    private AuditEventService auditEventService;

    private TeamService createService() {
        return new TeamService(
                teamRepository,
                playerRepository,
                coachRepository,
                fanRepository,
                teamInviteRepository,
                matchRepository,
                clipRepository,
                matchSquadMemberRepository,
                userRepository,
                minioObjectCleanupService,
                auditEventService);
    }

    @Test
    void getMyTeams_returnsCoachTeamsWhenAuthenticatedByKeycloakId() {
        UUID coachId = UUID.randomUUID();
        Team team = team("Arsenal");
        Coach coach = coach(coachId, "coach@test.com");
        coach.addTeam(team);

        JwtAuthenticationToken authentication = authentication(jwt(coachId.toString(), "coach@test.com"), "COACH");

        when(coachRepository.findById(coachId)).thenReturn(Optional.of(coach));

        TeamService teamService = createService();
        List<TeamResponse> teams = teamService.getMyTeams(authentication);

        assertThat(teams).hasSize(1);
        assertThat(teams.get(0).name()).isEqualTo("Arsenal");
    }

    @Test
    void getMyTeams_returnsPlayerTeamsWhenUuidLookupIsNeeded() {
        UUID playerId = UUID.randomUUID();
        Team team = team("Barcelona");
        Player player = player(playerId, "player@test.com");
        player.addTeam(team);

        JwtAuthenticationToken authentication = authentication(jwt(playerId.toString(), "player@test.com"), "PLAYER");

        when(playerRepository.findById(playerId)).thenReturn(Optional.of(player));

        TeamService teamService = createService();
        List<TeamResponse> teams = teamService.getMyTeams(authentication);

        assertThat(teams).hasSize(1);
        assertThat(teams.get(0).name()).isEqualTo("Barcelona");
    }

    @Test
    void getMyTeams_returnsFanTeamsWhenEmailFallbackIsUsed() {
        Team team = team("Liverpool");
        Fan fan = fan(UUID.randomUUID(), "fan@test.com");
        fan.addTeam(team);

        JwtAuthenticationToken authentication = authentication(jwt("", "fan@test.com"), "FAN");

        when(fanRepository.findByEmail("fan@test.com")).thenReturn(Optional.of(fan));

        TeamService teamService = createService();
        List<TeamResponse> teams = teamService.getMyTeams(authentication);

        assertThat(teams).hasSize(1);
        assertThat(teams.get(0).name()).isEqualTo("Liverpool");
    }

    @Test
    void getMyTeams_returnsAllTeamsForAdmin() {
        Team team = team("Arsenal");

        JwtAuthenticationToken authentication = authentication(jwt(UUID.randomUUID().toString(), "admin@test.com"), "ADMIN");

        when(teamRepository.findAll()).thenReturn(List.of(team));

        TeamService teamService = createService();
        List<TeamResponse> teams = teamService.getMyTeams(authentication);

        assertThat(teams).hasSize(1);
        assertThat(teams.get(0).name()).isEqualTo("Arsenal");
    }

    @Test
    void getMyTeams_rejectsMissingAuthentication() {
        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.getMyTeams(null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getMyTeams_rejectsUnsupportedRole() {
        JwtAuthenticationToken authentication = authentication(jwt(UUID.randomUUID().toString(), "coach@test.com"), "MANAGER");

        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.getMyTeams(authentication))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getMyTeams_rejectsCoachWhenNoAccountFound() {
        String subject = UUID.randomUUID().toString();
        JwtAuthenticationToken authentication = authentication(jwt(subject, "coach@test.com"), "COACH");

        when(coachRepository.findById(UUID.fromString(subject))).thenReturn(Optional.empty());
        when(coachRepository.findByEmail("coach@test.com")).thenReturn(Optional.empty());

        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.getMyTeams(authentication))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getMyTeams_rejectsPlayerWhenNoAccountFound() {
        UUID playerId = UUID.randomUUID();
        JwtAuthenticationToken authentication = authentication(jwt(playerId.toString(), "player@test.com"), "PLAYER");

        when(playerRepository.findById(playerId)).thenReturn(Optional.empty());
        when(playerRepository.findByEmail("player@test.com")).thenReturn(Optional.empty());

        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.getMyTeams(authentication))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getMyTeams_rejectsFanWhenNoAccountFound() {
        JwtAuthenticationToken authentication = authentication(jwt("", "fan@test.com"), "FAN");

        when(fanRepository.findByEmail("fan@test.com")).thenReturn(Optional.empty());

        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.getMyTeams(authentication))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getMyTeams_rejectsMissingEmailClaim() {
        JwtAuthenticationToken authentication = authentication(jwtWithoutEmail(UUID.randomUUID().toString()), "PLAYER");

        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.getMyTeams(authentication))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createTeam_allowsAdminWithoutCoachAccount() {
        CreateTeamRequest request = new CreateTeamRequest("Real Madrid", "RMA", "https://example.com/logo.png");
        Jwt adminJwt = jwtWithRole(UUID.randomUUID().toString(), "admin@test.com", "ADMIN");

        when(teamRepository.existsByName("Real Madrid")).thenReturn(false);
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        TeamService teamService = createService();
        TeamResponse response = teamService.createTeam(request, adminJwt);

        assertThat(response.name()).isEqualTo("Real Madrid");
        verify(coachRepository, never()).save(any(Coach.class));
    }

    @Test
    void createTeam_linksTeamToCoachWhenCallerIsCoach() {
        UUID coachId = UUID.randomUUID();
        CreateTeamRequest request = new CreateTeamRequest("Arsenal", "ARS", "https://example.com/arsenal.png");
        Jwt coachJwt = jwtWithRole(coachId.toString(), "coach@test.com", "COACH");
        Coach coach = coach(coachId, "coach@test.com");

        when(teamRepository.existsByName("Arsenal")).thenReturn(false);
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(coachRepository.findById(coachId)).thenReturn(Optional.of(coach));

        TeamService teamService = createService();
        TeamResponse response = teamService.createTeam(request, coachJwt);

        assertThat(response.name()).isEqualTo("Arsenal");
        verify(coachRepository).save(coach);
        assertThat(coach.getTeams()).extracting(Team::getName).contains("Arsenal");
    }

    @Test
    void deleteTeam_removesRelatedDataAndArtifacts() {
        UUID teamId = UUID.randomUUID();
        Team team = team(teamId, "Red Devils");

        Player player = player(UUID.randomUUID(), "player@test.com");
        player.addTeam(team);

        Coach coach = coach(UUID.randomUUID(), "coach@test.com");
        coach.addTeam(team);

        Fan fan = fan(UUID.randomUUID(), "fan@test.com");
        fan.addTeam(team);

        Match match = match(UUID.randomUUID(), team, null);
        Clip clip = clip(UUID.randomUUID(), match);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(matchRepository.findAllByHomeTeam_IdOrAwayTeam_Id(teamId, teamId)).thenReturn(List.of(match));
        when(clipRepository.findAllByMatch_IdIn(List.of(match.getId()))).thenReturn(List.of(clip));

        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
        when(jwt.getClaimAsString("email")).thenReturn("user@example.com");

        TeamService teamService = createService();
        teamService.deleteTeam(teamId, jwt);

        verify(teamInviteRepository).deleteAllByTeam_Id(teamId);
        verify(matchSquadMemberRepository).deleteAllByTeam_Id(teamId);
        verify(clipRepository).deleteAllByMatch_IdIn(List.of(match.getId()));
        verify(matchRepository).deleteAll(List.of(match));
        verify(teamRepository).delete(team);
        verify(teamRepository).flush();
        verify(minioObjectCleanupService).deleteMatchArtifactsForTeamDeletion(List.of(match), List.of(clip));
        verify(auditEventService).record(
            org.mockito.ArgumentMatchers.eq("TEAM_DELETED"),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq("TEAM"),
            org.mockito.ArgumentMatchers.eq(teamId.toString()),
            org.mockito.ArgumentMatchers.contains("matchIds=[" + match.getId() + "], clipIds=[" + clip.getId() + "]")
        );
    }

    @Test
    void deleteTeam_throwsWhenMissing() {
        UUID teamId = UUID.randomUUID();
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);

        TeamService teamService = createService();

        assertThatThrownBy(() -> teamService.deleteTeam(teamId, jwt))
                .isInstanceOf(NotFoundException.class);

        verify(matchRepository, never()).findAllByHomeTeam_IdOrAwayTeam_Id(any(), any());
    }

    private Jwt jwt(String subject, String email) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("email", email)
                .claim("preferred_username", email)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private Jwt jwtWithoutEmail(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private Jwt jwtWithRole(String subject, String email, String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("email", email)
                .claim("preferred_username", email)
                .claim("realm_access", Map.of("roles", List.of(role.toLowerCase())))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private JwtAuthenticationToken authentication(Jwt jwt, String role) {
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private Team team(String name) {
        return team(UUID.randomUUID(), name);
    }

    private Team team(UUID id, String name) {
        Team team = new Team();
        team.setId(id);
        team.setName(name);
        return team;
    }

    private Match match(UUID id, Team homeTeam, Team awayTeam) {
        Match match = new Match();
        match.setId(id);
        match.setHomeTeam(homeTeam);
        match.setAwayTeam(awayTeam);
        return match;
    }

    private Clip clip(UUID id, Match match) {
        Clip clip = new Clip();
        clip.setId(id);
        clip.setMatch(match);
        return clip;
    }

    private Coach coach(UUID id, String email) {
        Coach coach = new Coach();
        coach.setId(id);
        coach.setEmail(email);
        coach.setFirstName("Coach");
        coach.setLastName("One");
        return coach;
    }

    private Coach coach(UUID id, String keycloakId, String email) {
        return coach(id, email);
    }

    private Player player(UUID id, String email) {
        Player player = new Player();
        player.setId(id);
        player.setEmail(email);
        player.setFirstName("Player");
        player.setLastName("One");
        return player;
    }

    private Player player(UUID id, String keycloakId, String email) {
        return player(id, email);
    }

    private Fan fan(UUID id, String email) {
        Fan fan = new Fan();
        fan.setId(id);
        fan.setEmail(email);
        fan.setFirstName("Fan");
        fan.setLastName("One");
        return fan;
    }

    private Fan fan(UUID id, String keycloakId, String email) {
        return fan(id, email);
    }
}