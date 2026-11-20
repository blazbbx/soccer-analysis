package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.User;

import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.ClubAdminMembershipRepository;
import com.example.footballanalysis.repository.ClubRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
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
    @Mock private PlayerProfileRepository playerProfileRepository;
    @Mock private PlayerParentRepository playerParentRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private ClipRepository clipRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private ClubRepository clubRepository;
    @Mock private ClubAdminMembershipRepository clubAdminMembershipRepository;
    @Mock private TeamMembershipQuery teamMembershipQuery;
    // Hats: the token when the test sets one, otherwise what the test granted with TestHats.
    @org.mockito.Spy private ActorHats actorHats = new TestHats();

    @InjectMocks
    private UserAccessService userAccessService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void hasTeamAccess_rejectsAdminActorWhenSecurityContextIsAnotherUser() {
        User admin = admin();
        UUID teamId = UUID.randomUUID();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "coach",
                        "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_COACH"))));

        assertThat(userAccessService.isAdmin(admin)).isFalse();
        assertThat(userAccessService.hasTeamAccess(admin, List.of(teamId))).isFalse();
        assertThat(userAccessService.isAdmin(coach())).isFalse();
    }

    @Test
    void isAdmin_usesJwtOnlyWhenPrincipalMatchesActor() {
        User promoted = coach();
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
        User uploader = coach();
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
        User uploader = coach();
        User other = coach();
        Match match = new Match();
        match.setId(matchId);
        match.setUploadedBy(uploader);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        assertThat(userAccessService.canAccessMatch(other, matchId)).isFalse();
        assertThatThrownBy(() -> userAccessService.requireMatchAccess(other, matchId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void clubAdminCanOpenMatchOnlyForAClubTheyAdmin() {
        UUID matchId = UUID.randomUUID();
        UUID otherMatchId = UUID.randomUUID();
        UUID teamlessId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        UUID otherClubId = UUID.randomUUID();
        User clubAdmin = clubAdmin();
        Club club = new Club();
        club.setId(clubId);
        Club otherClub = new Club();
        otherClub.setId(otherClubId);
        Team home = new Team();
        home.setId(UUID.randomUUID());
        home.setClub(club);
        Team otherHome = new Team();
        otherHome.setId(UUID.randomUUID());
        otherHome.setClub(otherClub);
        Match match = new Match();
        match.setId(matchId);
        match.setHomeTeam(home);
        Match otherMatch = new Match();
        otherMatch.setId(otherMatchId);
        otherMatch.setHomeTeam(otherHome);
        Match teamless = new Match();
        teamless.setId(teamlessId);
        User someoneElse = coach();
        teamless.setUploadedBy(someoneElse);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(matchRepository.findById(otherMatchId)).thenReturn(Optional.of(otherMatch));
        when(matchRepository.findById(teamlessId)).thenReturn(Optional.of(teamless));
        when(clubAdminMembershipRepository.existsByUser_IdAndClub_Id(clubAdmin.getId(), clubId)).thenReturn(true);
        when(clubAdminMembershipRepository.existsByUser_IdAndClub_Id(clubAdmin.getId(), otherClubId)).thenReturn(false);

        assertThat(userAccessService.canAccessMatch(clubAdmin, matchId)).isTrue();
        assertThat(userAccessService.canAccessMatch(clubAdmin, otherMatchId)).isFalse();
        assertThat(userAccessService.canAccessMatch(clubAdmin, teamlessId)).isFalse();
        assertThat(userAccessService.hasTeamAccess(clubAdmin, List.of(home.getId()))).isFalse();
    }

    @Test
    void requireClubTeamManage_allowsClubAdminOfThatClub() {
        UUID clubId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        User clubAdmin = clubAdmin();
        Club club = new Club();
        club.setId(clubId);
        Team team = new Team();
        team.setId(teamId);
        team.setClub(club);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(clubAdminMembershipRepository.existsByUser_IdAndClub_Id(clubAdmin.getId(), clubId)).thenReturn(true);

        userAccessService.requireClubTeamManage(clubAdmin, teamId);
    }

    @Test
    void requireClubTeamManage_rejectsAnotherClubAndDoesNotGrantMatchAccess() {
        UUID clubId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        User clubAdmin = clubAdmin();
        Club club = new Club();
        club.setId(clubId);
        Team team = new Team();
        team.setId(teamId);
        team.setClub(club);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(clubAdminMembershipRepository.existsByUser_IdAndClub_Id(clubAdmin.getId(), clubId)).thenReturn(false);

        assertThatThrownBy(() -> userAccessService.requireClubTeamManage(clubAdmin, teamId))
                .isInstanceOf(ForbiddenException.class);
        assertThat(userAccessService.hasTeamAccess(clubAdmin, List.of(teamId))).isFalse();
        assertThat(userAccessService.canAccessTeamChat(clubAdmin, teamId)).isFalse();
    }

    @Test
    void canAccessTeamChat_allowsClubAdminOfThatClubOnly() {
        UUID clubId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        User clubAdmin = clubAdmin();
        Club club = new Club();
        club.setId(clubId);
        Team team = new Team();
        team.setId(teamId);
        team.setClub(club);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(clubAdminMembershipRepository.existsByUser_IdAndClub_Id(clubAdmin.getId(), clubId)).thenReturn(true);

        assertThat(userAccessService.canAccessTeamChat(clubAdmin, teamId)).isTrue();
        assertThat(userAccessService.hasTeamAccess(clubAdmin, List.of(teamId))).isFalse();
    }

    @Test
    void requireTeamAccess_throwsForbiddenWhenNotMember() {
        User coach = coach();
        UUID teamId = UUID.randomUUID();
        when(teamMembershipQuery.isCoachOnAnyTeam(coach.getId(), java.util.List.of(teamId))).thenReturn(false);

        assertThatThrownBy(() -> userAccessService.requireTeamAccess(coach, java.util.List.of(teamId)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getAccessibleTeamIds_includesFanTeams() {
        UUID fanId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        User fan = TestHats.user(UserRole.FAN);
        fan.setId(fanId);
        TestHats.grant(fan, UserRole.FAN);
        when(teamMembershipQuery.findFanTeamIds(fanId)).thenReturn(java.util.List.of(teamId));

        assertThat(userAccessService.getAccessibleTeamIds(fan)).containsExactly(teamId);
    }

    @Test
    void canAccessTeamChat_fanOfTheTeamIsRefused() {
        UUID teamId = UUID.randomUUID();
        User fan = TestHats.user(UserRole.FAN);
        fan.setId(UUID.randomUUID());
        TestHats.grant(fan, UserRole.FAN);
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        // Following the team still opens it for the fan elsewhere, but not its chat.
        assertThat(userAccessService.canAccessTeamChat(fan, teamId)).isFalse();
        assertThatThrownBy(() -> userAccessService.requireTeamChatAccess(fan, teamId))
                .isInstanceOf(ForbiddenException.class);
        org.mockito.Mockito.verify(teamMembershipQuery, org.mockito.Mockito.never())
                .isFanOnAnyTeam(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void canAccessTeamChat_fanWhoIsAlsoParentOfARosteredPlayerIsAllowed() {
        UUID teamId = UUID.randomUUID();
        User user = TestHats.user(UserRole.FAN);
        user.setId(UUID.randomUUID());
        TestHats.grant(user, UserRole.FAN);
        TestHats.grant(user, UserRole.PARENT);
        when(playerParentRepository.existsByUser_IdAndPlayerProfile_Team_IdIn(user.getId(), List.of(teamId)))
                .thenReturn(true);

        assertThat(userAccessService.canAccessTeamChat(user, teamId)).isTrue();
    }

    @Test
    void teamMemberCanAccessMatchForOwnTeam() {
        UUID matchId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        User coach = coach();
        Team team = new Team();
        team.setId(teamId);
        Match match = new Match();
        match.setId(matchId);
        match.setHomeTeam(team);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(teamMembershipQuery.isCoachOnAnyTeam(coach.getId(), java.util.List.of(teamId))).thenReturn(true);
        when(teamMembershipQuery.isCoachOnTeam(coach.getId(), teamId)).thenReturn(true);

        assertThat(userAccessService.canAccessMatch(coach, matchId)).isTrue();
        assertThat(userAccessService.canManageMatch(coach, matchId)).isTrue();
    }

    @Test
    void awayCoachCanViewButNotManageMatch() {
        UUID matchId = UUID.randomUUID();
        UUID homeTeamId = UUID.randomUUID();
        UUID awayTeamId = UUID.randomUUID();
        User awayCoach = coach();
        Team home = new Team();
        home.setId(homeTeamId);
        Team away = new Team();
        away.setId(awayTeamId);
        Match match = new Match();
        match.setId(matchId);
        match.setHomeTeam(home);
        match.setAwayTeam(away);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(teamMembershipQuery.isCoachOnAnyTeam(awayCoach.getId(), java.util.List.of(homeTeamId, awayTeamId)))
                .thenReturn(true);
        when(teamMembershipQuery.isCoachOnTeam(awayCoach.getId(), homeTeamId))
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
        User homeCoach = coach();
        Team home = new Team();
        home.setId(homeTeamId);
        Match match = new Match();
        match.setId(matchId);
        match.setHomeTeam(home);

        when(clipRepository.findMatchIdByClipId(clipId)).thenReturn(matchId);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(teamMembershipQuery.isCoachOnAnyTeam(homeCoach.getId(), java.util.List.of(homeTeamId)))
                .thenReturn(true);
        when(teamMembershipQuery.isCoachOnTeam(homeCoach.getId(), homeTeamId))
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
        User stranger = coach();
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

    @Test
    void hasTeamAccess_playerFollowsLinkedProfileTeamOnly() {
        UUID teamId = UUID.randomUUID();
        User player = TestHats.user(UserRole.PLAYER);
        player.setId(UUID.randomUUID());
        TestHats.grant(player, UserRole.PLAYER);

        when(playerProfileRepository.existsByLinkedUser_IdAndTeam_IdIn(player.getId(), List.of(teamId)))
                .thenReturn(true);
        assertThat(userAccessService.hasTeamAccess(player, List.of(teamId))).isTrue();

        when(playerProfileRepository.existsByLinkedUser_IdAndTeam_IdIn(player.getId(), List.of(teamId)))
                .thenReturn(false);
        assertThat(userAccessService.hasTeamAccess(player, List.of(teamId))).isFalse();
    }

    @Test
    void getAccessibleTeamIds_playerUsesLinkedProfile() {
        User player = TestHats.user(UserRole.PLAYER);
        player.setId(UUID.randomUUID());
        TestHats.grant(player, UserRole.PLAYER);
        UUID teamId = UUID.randomUUID();
        when(playerProfileRepository.findTeamIdsByLinkedUserId(player.getId())).thenReturn(List.of(teamId));

        assertThat(userAccessService.getAccessibleTeamIds(player)).containsExactly(teamId);
    }

    @Test
    void hasTeamAccess_parentUsesChildTeam() {
        UUID teamId = UUID.randomUUID();
        User parent = TestHats.user(UserRole.PARENT);
        parent.setId(UUID.randomUUID());
        TestHats.grant(parent, UserRole.PARENT);
        when(playerParentRepository.existsByUser_IdAndPlayerProfile_Team_IdIn(parent.getId(), List.of(teamId)))
                .thenReturn(true);

        assertThat(userAccessService.hasTeamAccess(parent, List.of(teamId))).isTrue();

        when(playerParentRepository.existsByUser_IdAndPlayerProfile_Team_IdIn(parent.getId(), List.of(teamId)))
                .thenReturn(false);
        assertThat(userAccessService.hasTeamAccess(parent, List.of(teamId))).isFalse();
    }

    @Test
    void hasClubAccess_usesMembershipAndIgnoresCreator() {
        UUID clubId = UUID.randomUUID();
        Club club = new Club();
        club.setId(clubId);
        club.setCreatedByUserId(UUID.randomUUID());
        User creator = coach();
        creator.setId(club.getCreatedByUserId());
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club));
        when(teamRepository.findIdsByClub_Id(clubId)).thenReturn(List.of());

        assertThat(userAccessService.hasClubAccess(creator, clubId)).isFalse();

        User clubAdmin = TestHats.user(UserRole.CLUB_ADMIN);
        clubAdmin.setId(UUID.randomUUID());
        TestHats.grant(clubAdmin, UserRole.CLUB_ADMIN);
        when(clubAdminMembershipRepository.existsByUser_IdAndClub_Id(clubAdmin.getId(), clubId)).thenReturn(true);

        assertThat(userAccessService.hasClubAccess(clubAdmin, clubId)).isTrue();
        assertThat(userAccessService.isClubAdmin(clubAdmin, clubId)).isTrue();
    }

    @Test
    void requireClubAdmin_rejectsCoachAndAllowsPlatformAdminWithoutRow() {
        UUID clubId = UUID.randomUUID();
        User coach = coach();

        assertThatThrownBy(() -> userAccessService.requireClubAdmin(coach, clubId))
                .isInstanceOf(ForbiddenException.class);

        userAccessService.requireClubAdmin(admin(), clubId);
    }

    @Test
    void requireOwnClubAdmin_rejectsPlatformAdminWithoutRowAndAllowsTheClubsOwnAdmin() {
        UUID clubId = UUID.randomUUID();
        User clubAdmin = clubAdmin();
        when(clubAdminMembershipRepository.existsByUser_IdAndClub_Id(clubAdmin.getId(), clubId)).thenReturn(true);

        assertThatThrownBy(() -> userAccessService.requireOwnClubAdmin(admin(), clubId))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> userAccessService.requireOwnClubAdmin(coach(), clubId))
                .isInstanceOf(ForbiddenException.class);

        userAccessService.requireOwnClubAdmin(clubAdmin, clubId);
    }

    @Test
    void requireOwnClubAdmin_rejectsPlatformAdminEvenWithTheHatAndARow() {
        UUID clubId = UUID.randomUUID();
        User operator = admin();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(operator.getId().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(
                        new SimpleGrantedAuthority("ROLE_admin"),
                        new SimpleGrantedAuthority("ROLE_club_admin"))));
        when(clubAdminMembershipRepository.existsByUser_IdAndClub_Id(operator.getId(), clubId)).thenReturn(true);

        assertThat(userAccessService.isClubAdmin(operator, clubId)).isTrue();
        assertThatThrownBy(() -> userAccessService.requireOwnClubAdmin(operator, clubId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void leftoverCoachRowWithoutCoachHatDoesNotGrantTeamAccess() {
        UUID teamId = UUID.randomUUID();
        User parent = TestHats.user(UserRole.PARENT);
        parent.setId(UUID.randomUUID());
        TestHats.grant(parent, UserRole.PARENT);
        when(playerParentRepository.existsByUser_IdAndPlayerProfile_Team_IdIn(parent.getId(), List.of(teamId)))
                .thenReturn(false);

        assertThat(userAccessService.hasHat(parent, UserRole.COACH)).isFalse();
        assertThat(userAccessService.hasTeamAccess(parent, List.of(teamId))).isFalse();
    }

    @Test
    void dualHatParentWithCoachJwtUsesCoachMembership() {
        UUID teamId = UUID.randomUUID();
        User parent = TestHats.user(UserRole.PARENT);
        parent.setId(UUID.randomUUID());
        TestHats.grant(parent, UserRole.PARENT);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(parent.getId().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(
                        new SimpleGrantedAuthority("ROLE_parent"),
                        new SimpleGrantedAuthority("ROLE_coach"))));
        when(teamMembershipQuery.isCoachOnAnyTeam(parent.getId(), List.of(teamId))).thenReturn(true);

        assertThat(userAccessService.hasHat(parent, UserRole.COACH)).isTrue();
        assertThat(userAccessService.hasHat(parent, UserRole.PARENT)).isTrue();
        assertThat(userAccessService.hasTeamAccess(parent, List.of(teamId))).isTrue();
    }

    @Test
    void requireTravelReadAndWrite_followClubMembershipAndLeaveFansOut() {
        UUID clubId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID otherTeamId = UUID.randomUUID();
        User clubAdmin = clubAdmin();
        Club club = new Club();
        club.setId(clubId);
        Team team = new Team();
        team.setId(teamId);
        team.setClub(club);
        Team other = new Team();
        other.setId(otherTeamId);
        other.setClub(new Club());
        other.getClub().setId(UUID.randomUUID());
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamRepository.findById(otherTeamId)).thenReturn(Optional.of(other));
        when(clubAdminMembershipRepository.existsByUser_IdAndClub_Id(clubAdmin.getId(), clubId)).thenReturn(true);

        userAccessService.requireTravelRead(clubAdmin, teamId);
        userAccessService.requireTravelWrite(clubAdmin, teamId);
        assertThatThrownBy(() -> userAccessService.requireTravelRead(clubAdmin, otherTeamId))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> userAccessService.requireTravelWrite(clubAdmin, otherTeamId))
                .isInstanceOf(ForbiddenException.class);

        User fan = TestHats.user(UserRole.FAN);
        fan.setId(UUID.randomUUID());
        TestHats.grant(fan, UserRole.FAN);
        assertThatThrownBy(() -> userAccessService.requireTravelRead(fan, teamId))
                .isInstanceOf(ForbiddenException.class);

        User coach = coach();
        when(teamMembershipQuery.isCoachOnTeam(coach.getId(), teamId)).thenReturn(true);
        userAccessService.requireTravelRead(coach, teamId);
        userAccessService.requireTravelWrite(coach, teamId);

        User parent = TestHats.user(UserRole.PARENT);
        parent.setId(UUID.randomUUID());
        TestHats.grant(parent, UserRole.PARENT);
        when(playerParentRepository.existsByUser_IdAndPlayerProfile_Team_IdIn(parent.getId(), List.of(teamId))).thenReturn(true);
        userAccessService.requireTravelRead(parent, teamId);
        assertThatThrownBy(() -> userAccessService.requireTravelWrite(parent, teamId))
                .isInstanceOf(ForbiddenException.class);

        User player = TestHats.user(UserRole.PLAYER);
        player.setId(UUID.randomUUID());
        TestHats.grant(player, UserRole.PLAYER);
        when(playerProfileRepository.existsByLinkedUser_IdAndTeam_Id(player.getId(), teamId)).thenReturn(true);
        userAccessService.requireTravelRead(player, teamId);
        assertThatThrownBy(() -> userAccessService.requireTravelWrite(player, teamId))
                .isInstanceOf(ForbiddenException.class);

        assertThatThrownBy(() -> userAccessService.requireTravelRead(clubAdmin, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.team.not_found");
    }

    private User clubAdmin() {
        User clubAdmin = TestHats.user(UserRole.CLUB_ADMIN);
        clubAdmin.setId(UUID.randomUUID());
        TestHats.grant(clubAdmin, UserRole.CLUB_ADMIN);
        return clubAdmin;
    }

    private User coach() {
        User coach = TestHats.user(UserRole.COACH);
        coach.setId(UUID.randomUUID());
        TestHats.grant(coach, UserRole.COACH);
        return coach;
    }

    private User admin() {
        User admin = TestHats.user(UserRole.ADMIN);
        admin.setId(UUID.randomUUID());
        TestHats.grant(admin, UserRole.ADMIN);
        return admin;
    }
}
