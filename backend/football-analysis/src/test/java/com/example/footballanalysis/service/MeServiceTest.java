package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.ClubAdminMembership;
import com.example.footballanalysis.model.db.ClubAdminMembershipId;
import com.example.footballanalysis.model.db.PlayerParent;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.MeViewsResponse;
import com.example.footballanalysis.repository.ClubAdminMembershipRepository;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MeService views")
class MeServiceTest {

    @Mock private UserAccessService userAccessService;
    @Mock private UserRoleSyncService userRoleSyncService;
    @Mock private ClubAdminMembershipRepository clubAdminMembershipRepository;
    @Mock private TeamMembershipQuery teamMembershipQuery;
    @Mock private PlayerParentRepository playerParentRepository;
    @Mock private PlayerProfileRepository playerProfileRepository;

    @InjectMocks
    private MeService meService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void leftoverCoachRowWithoutCoachHatIsOmitted() {
        UUID profileId = UUID.randomUUID();
        Parent parent = parent();
        Jwt jwt = jwtFor(parent.getId());
        authenticate(jwt, "ROLE_parent");
        when(userAccessService.resolveCurrentUser(jwt)).thenReturn(parent);
        when(userRoleSyncService.extractAllClientRoles(jwt)).thenReturn(EnumSet.of(UserRole.PARENT));
        when(userAccessService.hasHat(parent, UserRole.CLUB_ADMIN)).thenReturn(false);
        when(userAccessService.hasHat(parent, UserRole.COACH)).thenReturn(false);
        when(userAccessService.hasHat(parent, UserRole.PARENT)).thenReturn(true);
        when(userAccessService.hasHat(parent, UserRole.PLAYER)).thenReturn(false);
        when(userAccessService.hasHat(parent, UserRole.FAN)).thenReturn(false);
        PlayerParent link = new PlayerParent();
        link.setId(new PlayerParentId(parent.getId(), profileId));
        when(playerParentRepository.findByUser_Id(parent.getId())).thenReturn(List.of(link));

        MeViewsResponse views = meService.getViews(jwt);

        assertThat(views.primaryRole()).isEqualTo(UserRole.PARENT);
        assertThat(views.hats()).containsExactly(UserRole.PARENT);
        assertThat(views.coachTeamIds()).isEmpty();
        assertThat(views.parentProfileIds()).containsExactly(profileId);
        verify(teamMembershipQuery, never()).findCoachTeamIds(parent.getId());
    }

    @Test
    void dualHatListsCoachTeamsAndParentProfiles() {
        UUID teamId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Parent parent = parent();
        Jwt jwt = jwtFor(parent.getId());
        authenticate(jwt, "ROLE_parent", "ROLE_coach");
        when(userAccessService.resolveCurrentUser(jwt)).thenReturn(parent);
        when(userRoleSyncService.extractAllClientRoles(jwt))
                .thenReturn(EnumSet.of(UserRole.PARENT, UserRole.COACH));
        when(userAccessService.hasHat(parent, UserRole.CLUB_ADMIN)).thenReturn(false);
        when(userAccessService.hasHat(parent, UserRole.COACH)).thenReturn(true);
        when(userAccessService.hasHat(parent, UserRole.PARENT)).thenReturn(true);
        when(userAccessService.hasHat(parent, UserRole.PLAYER)).thenReturn(false);
        when(userAccessService.hasHat(parent, UserRole.FAN)).thenReturn(false);
        when(teamMembershipQuery.findCoachTeamIds(parent.getId())).thenReturn(List.of(teamId));
        PlayerParent link = new PlayerParent();
        link.setId(new PlayerParentId(parent.getId(), profileId));
        when(playerParentRepository.findByUser_Id(parent.getId())).thenReturn(List.of(link));

        MeViewsResponse views = meService.getViews(jwt);

        assertThat(views.hats()).containsExactly(UserRole.COACH, UserRole.PARENT);
        assertThat(views.coachTeamIds()).containsExactly(teamId);
        assertThat(views.parentProfileIds()).containsExactly(profileId);
    }

    @Test
    void clubAdminHatListsClubs() {
        UUID clubId = UUID.randomUUID();
        Coach coach = coach();
        Jwt jwt = jwtFor(coach.getId());
        authenticate(jwt, "ROLE_coach", "ROLE_club_admin");
        when(userAccessService.resolveCurrentUser(jwt)).thenReturn(coach);
        when(userRoleSyncService.extractAllClientRoles(jwt))
                .thenReturn(EnumSet.of(UserRole.COACH, UserRole.CLUB_ADMIN));
        when(userAccessService.hasHat(coach, UserRole.CLUB_ADMIN)).thenReturn(true);
        when(userAccessService.hasHat(coach, UserRole.COACH)).thenReturn(true);
        when(userAccessService.hasHat(coach, UserRole.PARENT)).thenReturn(false);
        when(userAccessService.hasHat(coach, UserRole.PLAYER)).thenReturn(false);
        when(userAccessService.hasHat(coach, UserRole.FAN)).thenReturn(false);
        ClubAdminMembership membership = new ClubAdminMembership();
        membership.setId(new ClubAdminMembershipId(coach.getId(), clubId));
        when(clubAdminMembershipRepository.findByUser_Id(coach.getId())).thenReturn(List.of(membership));
        when(teamMembershipQuery.findCoachTeamIds(coach.getId())).thenReturn(List.of());

        MeViewsResponse views = meService.getViews(jwt);

        assertThat(views.hats()).containsExactly(UserRole.CLUB_ADMIN, UserRole.COACH);
        assertThat(views.clubAdminClubIds()).containsExactly(clubId);
        assertThat(views.primaryRole()).isEqualTo(UserRole.COACH);
    }

    private static Parent parent() {
        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setRole(UserRole.PARENT);
        return parent;
    }

    private static Coach coach() {
        Coach coach = new Coach();
        coach.setId(UUID.randomUUID());
        coach.setRole(UserRole.COACH);
        return coach;
    }

    private static Jwt jwtFor(UUID userId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    private static void authenticate(Jwt jwt, String... roles) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }
}
