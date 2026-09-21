package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.ClubRepository;
import com.example.footballanalysis.repository.CoachRepository;
import com.example.footballanalysis.repository.FanRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.PlayerRepository;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserAccessService ACL")
class UserAccessServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private CoachRepository coachRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private FanRepository fanRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private ClipRepository clipRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private ClubRepository clubRepository;

    @InjectMocks
    private UserAccessService userAccessService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void hasTeamAccess_adminIsAllowedEvenWhenSecurityContextIsAnotherUser() {
        Admin admin = admin();
        UUID teamId = UUID.randomUUID();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "coach",
                        "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_COACH"))));

        assertThat(userAccessService.isAdmin(admin)).isTrue();
        assertThat(userAccessService.hasTeamAccess(admin, List.of(teamId))).isTrue();
        assertThat(userAccessService.isAdmin(coach())).isFalse();
    }

    @Test
    void isAdmin_usesJwtOnlyWhenPrincipalMatchesActor() {
        Coach promoted = coach();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(promoted.getId().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertThat(userAccessService.isAdmin(promoted)).isTrue();
        assertThat(userAccessService.isAdmin(coach())).isFalse();
    }

    @Test
    void uploaderCanAccessMatchWithoutTeams() {
        UUID matchId = UUID.randomUUID();
        Coach uploader = coach();
        Match match = new Match();
        match.setId(matchId);
        match.setUploadedBy(uploader);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        assertThat(userAccessService.canAccessMatch(uploader, matchId)).isTrue();
        userAccessService.requireMatchAccess(uploader, matchId);
    }

    @Test
    void otherCoachCannotAccessUnattachedMatch() {
        UUID matchId = UUID.randomUUID();
        Coach uploader = coach();
        Coach other = coach();
        Match match = new Match();
        match.setId(matchId);
        match.setUploadedBy(uploader);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        assertThat(userAccessService.canAccessMatch(other, matchId)).isFalse();
        assertThatThrownBy(() -> userAccessService.requireMatchAccess(other, matchId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireTeamAccess_throwsForbiddenWhenNotMember() {
        Coach coach = coach();
        UUID teamId = UUID.randomUUID();
        when(coachRepository.existsByIdAndTeams_IdIn(coach.getId(), java.util.List.of(teamId))).thenReturn(false);

        assertThatThrownBy(() -> userAccessService.requireTeamAccess(coach, java.util.List.of(teamId)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getAccessibleTeamIds_includesFanTeams() {
        UUID fanId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        com.example.footballanalysis.model.db.user.Fan fan = new com.example.footballanalysis.model.db.user.Fan();
        fan.setId(fanId);
        fan.setRole(UserRole.FAN);
        when(fanRepository.findTeamIdsByFanId(fanId)).thenReturn(java.util.List.of(teamId));

        assertThat(userAccessService.getAccessibleTeamIds(fan)).containsExactly(teamId);
    }

    @Test
    void teamMemberCanAccessMatchForOwnTeam() {
        UUID matchId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Coach coach = coach();
        Team team = new Team();
        team.setId(teamId);
        Match match = new Match();
        match.setId(matchId);
        match.setHomeTeam(team);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(coachRepository.existsByIdAndTeams_IdIn(coach.getId(), java.util.List.of(teamId))).thenReturn(true);

        assertThat(userAccessService.canAccessMatch(coach, matchId)).isTrue();
        assertThat(userAccessService.canManageMatch(coach, matchId)).isTrue();
    }

    @Test
    void awayCoachCanViewButNotManageMatch() {
        UUID matchId = UUID.randomUUID();
        UUID homeTeamId = UUID.randomUUID();
        UUID awayTeamId = UUID.randomUUID();
        Coach awayCoach = coach();
        Team home = new Team();
        home.setId(homeTeamId);
        Team away = new Team();
        away.setId(awayTeamId);
        Match match = new Match();
        match.setId(matchId);
        match.setHomeTeam(home);
        match.setAwayTeam(away);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(coachRepository.existsByIdAndTeams_IdIn(awayCoach.getId(), java.util.List.of(homeTeamId, awayTeamId)))
                .thenReturn(true);
        when(coachRepository.existsByIdAndTeams_IdIn(awayCoach.getId(), java.util.List.of(homeTeamId)))
                .thenReturn(false);

        assertThat(userAccessService.canAccessMatch(awayCoach, matchId)).isTrue();
        assertThat(userAccessService.canManageMatch(awayCoach, matchId)).isFalse();
        assertThatThrownBy(() -> userAccessService.requireMatchManageAccess(awayCoach, matchId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void clipAccessFollowsMatchAccess() {
        UUID clipId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID homeTeamId = UUID.randomUUID();
        Coach homeCoach = coach();
        Team home = new Team();
        home.setId(homeTeamId);
        Match match = new Match();
        match.setId(matchId);
        match.setHomeTeam(home);

        when(clipRepository.findMatchIdByClipId(clipId)).thenReturn(matchId);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(coachRepository.existsByIdAndTeams_IdIn(homeCoach.getId(), java.util.List.of(homeTeamId)))
                .thenReturn(true);

        assertThat(userAccessService.canAccessClip(homeCoach, clipId)).isTrue();
        assertThat(userAccessService.canManageClip(homeCoach, clipId)).isTrue();
        userAccessService.requireClipAccess(homeCoach, clipId);
        userAccessService.requireClipManageAccess(homeCoach, clipId);
    }

    @Test
    void strangerCannotAccessOrManageClip() {
        UUID clipId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        Coach stranger = coach();
        Match match = new Match();
        match.setId(matchId);

        when(clipRepository.findMatchIdByClipId(clipId)).thenReturn(matchId);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        assertThat(userAccessService.canAccessClip(stranger, clipId)).isFalse();
        assertThat(userAccessService.canManageClip(stranger, clipId)).isFalse();
        assertThatThrownBy(() -> userAccessService.requireClipAccess(stranger, clipId))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> userAccessService.requireClipManageAccess(stranger, clipId))
                .isInstanceOf(ForbiddenException.class);
    }

    private Coach coach() {
        Coach coach = new Coach();
        coach.setId(UUID.randomUUID());
        coach.setRole(UserRole.COACH);
        return coach;
    }

    private Admin admin() {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        admin.setRole(UserRole.ADMIN);
        return admin;
    }
}
