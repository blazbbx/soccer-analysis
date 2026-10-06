package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.CreateClubRequest;
import com.example.footballanalysis.model.requests.CreateTeamRequest;
import com.example.footballanalysis.model.requests.UpdateClubRequest;
import com.example.footballanalysis.model.responses.ClubResponse;
import com.example.footballanalysis.model.responses.TeamResponse;
import com.example.footballanalysis.repository.ClubAdminMembershipRepository;
import com.example.footballanalysis.repository.ClubRepository;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClubService tests")
class ClubServiceTest {

    @Mock
    private ClubRepository clubRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamService teamService;
    @Mock
    private UserAccessService userAccessService;
    @Mock
    private AuditEventService auditEventService;
    @Mock
    private ClubAdminMembershipRepository clubAdminMembershipRepository;
    @Mock
    private UserRepository userRepository;

    private ClubService service() {
        return new ClubService(
                clubRepository, teamRepository, teamService, userAccessService, auditEventService, clubAdminMembershipRepository, userRepository);
    }

    @Test
    void createClub_persistsAndAudits() {
        UUID adminId = UUID.randomUUID();
        Jwt jwt = jwt(adminId);
        Admin admin = new Admin();
        admin.setId(adminId);
        admin.setRole(UserRole.ADMIN);
        when(userAccessService.resolveCurrentUser(jwt)).thenReturn(admin);
        when(userAccessService.isAdmin(admin)).thenReturn(true);
        when(clubRepository.save(any(Club.class))).thenAnswer(invocation -> {
            Club saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            return saved;
        });

        ClubResponse response = service().createClub(
                new CreateClubRequest("Vasas", "VAS", null), jwt);

        assertThat(response.name()).isEqualTo("Vasas");
        assertThat(response.shortName()).isEqualTo("VAS");
        verify(auditEventService).record(
                org.mockito.ArgumentMatchers.eq("CLUB_CREATED"),
                org.mockito.ArgumentMatchers.eq(adminId),
                org.mockito.ArgumentMatchers.eq("ADMIN"),
                org.mockito.ArgumentMatchers.eq("CLUB"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("name=Vasas"));
    }

    @Test
    void createTeamInClub_doesNotBindActorAsCoach() {
        UUID clubId = UUID.randomUUID();
        Jwt jwt = jwt(UUID.randomUUID());
        Admin admin = new Admin();
        admin.setId(UUID.fromString(jwt.getSubject()));
        admin.setRole(UserRole.ADMIN);
        CreateTeamRequest request = new CreateTeamRequest("U13", "U13", null);
        TeamResponse created = new TeamResponse(
                UUID.randomUUID(), "U13", "U13", null, clubId, "Vasas", List.of(), List.of(), List.of(), List.of());
        when(userAccessService.resolveCurrentUser(jwt)).thenReturn(admin);
        when(clubRepository.existsById(clubId)).thenReturn(true);
        when(teamService.createTeam(any(CreateTeamRequest.class), eq(admin))).thenReturn(created);

        TeamResponse response = service().createTeamInClub(clubId, request, jwt);

        assertThat(response.name()).isEqualTo("U13");
        verify(teamService).createTeam(new CreateTeamRequest("U13", "U13", null, clubId), admin);
    }

    @Test
    void getClub_notFound() {
        UUID clubId = UUID.randomUUID();
        Jwt jwt = jwt(UUID.randomUUID());
        Admin admin = new Admin();
        admin.setId(UUID.fromString(jwt.getSubject()));
        admin.setRole(UserRole.ADMIN);
        when(userAccessService.resolveCurrentUser(jwt)).thenReturn(admin);
        when(clubRepository.findById(clubId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getClub(clubId, jwt))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listClubTeams_forbiddenWhenNoAccess() {
        UUID clubId = UUID.randomUUID();
        Jwt jwt = jwt(UUID.randomUUID());
        Coach coach = new Coach();
        coach.setId(UUID.fromString(jwt.getSubject()));
        coach.setRole(UserRole.COACH);
        when(userAccessService.resolveCurrentUser(jwt)).thenReturn(coach);
        when(clubRepository.existsById(clubId)).thenReturn(true);
        when(userAccessService.hasClubAccess(coach, clubId)).thenReturn(false);

        assertThatThrownBy(() -> service().listClubTeams(clubId, jwt))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listClubs_adminSeesAll() {
        Jwt jwt = jwt(UUID.randomUUID());
        Admin admin = new Admin();
        admin.setId(UUID.fromString(jwt.getSubject()));
        admin.setRole(UserRole.ADMIN);
        Club club = new Club();
        club.setId(UUID.randomUUID());
        club.setName("Fradi");
        when(userAccessService.resolveCurrentUser(jwt)).thenReturn(admin);
        when(userAccessService.isAdmin(admin)).thenReturn(true);
        when(clubRepository.findAll()).thenReturn(List.of(club));

        assertThat(service().listClubs(jwt)).extracting(ClubResponse::name).containsExactly("Fradi");
    }

    @Test
    void updateClub_changesName() {
        UUID clubId = UUID.randomUUID();
        Jwt jwt = jwt(UUID.randomUUID());
        Admin admin = new Admin();
        admin.setId(UUID.fromString(jwt.getSubject()));
        admin.setRole(UserRole.ADMIN);
        Club club = new Club();
        club.setId(clubId);
        club.setName("Old");
        when(userAccessService.resolveCurrentUser(jwt)).thenReturn(admin);
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club));
        when(clubRepository.save(club)).thenReturn(club);

        ClubResponse response = service().updateClub(
                clubId, new UpdateClubRequest("New", "N", null), jwt);

        assertThat(response.name()).isEqualTo("New");
        verify(auditEventService).record(
                org.mockito.ArgumentMatchers.eq("CLUB_UPDATED"),
                org.mockito.ArgumentMatchers.eq(admin.getId()),
                org.mockito.ArgumentMatchers.eq("ADMIN"),
                org.mockito.ArgumentMatchers.eq("CLUB"),
                org.mockito.ArgumentMatchers.eq(clubId.toString()),
                org.mockito.ArgumentMatchers.eq("name=New"));
    }

    @Test
    void deleteClub_removesEmptyClub() {
        UUID clubId = UUID.randomUUID();
        Jwt jwt = jwt(UUID.randomUUID());
        Admin admin = new Admin();
        admin.setId(UUID.fromString(jwt.getSubject()));
        admin.setRole(UserRole.ADMIN);
        Club club = new Club();
        club.setId(clubId);
        club.setName("Gone");
        when(userAccessService.resolveCurrentUser(jwt)).thenReturn(admin);
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club));
        when(teamRepository.findByClub_Id(clubId)).thenReturn(List.of());

        service().deleteClub(clubId, jwt);

        verify(clubRepository).delete(club);
        verify(auditEventService).record(
                org.mockito.ArgumentMatchers.eq("CLUB_DELETED"),
                org.mockito.ArgumentMatchers.eq(admin.getId()),
                org.mockito.ArgumentMatchers.eq("ADMIN"),
                org.mockito.ArgumentMatchers.eq("CLUB"),
                org.mockito.ArgumentMatchers.eq(clubId.toString()),
                org.mockito.ArgumentMatchers.eq("name=Gone"));
    }

    @Test
    void deleteClub_rejectsWhenTeamsRemain() {
        UUID clubId = UUID.randomUUID();
        Jwt jwt = jwt(UUID.randomUUID());
        Admin admin = new Admin();
        admin.setId(UUID.fromString(jwt.getSubject()));
        admin.setRole(UserRole.ADMIN);
        Club club = new Club();
        club.setId(clubId);
        club.setName("Busy");
        Team team = new Team();
        team.setId(UUID.randomUUID());
        when(userAccessService.resolveCurrentUser(jwt)).thenReturn(admin);
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club));
        when(teamRepository.findByClub_Id(clubId)).thenReturn(List.of(team));

        assertThatThrownBy(() -> service().deleteClub(clubId, jwt))
                .isInstanceOf(ConflictException.class);
        verify(clubRepository, org.mockito.Mockito.never()).delete(club);
    }

    private Jwt jwt(UUID subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject.toString())
                .claim("email", "user@test.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
