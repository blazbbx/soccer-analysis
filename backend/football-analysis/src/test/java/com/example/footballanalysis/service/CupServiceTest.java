package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.User;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.model.db.Cup;
import com.example.footballanalysis.model.db.CupMatch;
import com.example.footballanalysis.model.db.CupTeam;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.CreateCupRequest;
import com.example.footballanalysis.model.requests.CupMatchScheduleRequest;
import com.example.footballanalysis.model.requests.CupTeamCreateRequest;
import com.example.footballanalysis.model.responses.CupListItem;
import com.example.footballanalysis.model.responses.CupMatchResponse;
import com.example.footballanalysis.model.responses.CupResponse;
import com.example.footballanalysis.repository.CupMatchRepository;
import com.example.footballanalysis.repository.CupRepository;
import com.example.footballanalysis.repository.CupTeamRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CupService")
class CupServiceTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 1, 15, 0);

    @Mock private CupRepository cupRepository;
    @Mock private CupTeamRepository cupTeamRepository;
    @Mock private CupMatchRepository cupMatchRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private AuditEventService auditEventService;
    @Mock private UserAccessService userAccessService;
    @Mock private CupStandingsService standingsService;

    private CupService cupService;

    @BeforeEach
    void setUp() {
        CupAccessPolicy access = new CupAccessPolicy(cupRepository, cupTeamRepository, cupMatchRepository, userAccessService);
        cupService = new CupService(cupRepository, cupTeamRepository, cupMatchRepository, teamRepository, matchRepository,
                auditEventService, userAccessService, access, standingsService);
    }

    // ===== List =====

    @Test
    void listAllCups_returnsEmptyWhenActorMissing() {
        assertThat(cupService.listAllCups(null, null, null, null, null).items()).isEmpty();
    }

    @Test
    void listAllCups_returnsCreatedCupsWhenActorHasNoTeams() {
        User coach = coach();
        Cup cup = cupOwnedBy(coach);
        var item = new CupListItem(cup.getId(), cup.getName(), cup.getCreatedAt(), coach.getId(), "Ada", "Coach", 1, 0);
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(userAccessService.getAccessibleTeamIds(coach)).thenReturn(Set.of());
        when(cupRepository.findListItemsCreatedBy(eq(coach.getId()), any())).thenReturn(List.of(item));
        when(cupRepository.countListItemsCreatedBy(coach.getId())).thenReturn(1L);

        var result = cupService.listAllCups(coach, null, null, null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo(cup.getId());
        assertThat(result.items().get(0).createdById()).isEqualTo(coach.getId());
        assertThat(result.items().get(0).canManage()).isTrue();
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void listAllCups_usesTheRequestedChildTeams() {
        User coach = coach();
        UUID parentTeamId = UUID.randomUUID();
        UUID cupId = UUID.randomUUID();
        var item = new CupListItem(cupId, "Youth", null, UUID.randomUUID(), "Ada", "Coach", 2, 1);
        when(userAccessService.getAccessibleTeamIds(coach, UserRole.PARENT)).thenReturn(Set.of(parentTeamId));
        when(cupRepository.findListItemsContainingTeams(eq(Set.of(parentTeamId)), any())).thenReturn(List.of(item));
        when(cupRepository.countListItemsContainingTeams(Set.of(parentTeamId))).thenReturn(1L);

        var result = cupService.listAllCups(coach, UserRole.PARENT, List.of(parentTeamId), 1, 20);

        assertThat(result.items()).extracting(CupListItem::id).containsExactly(cupId);
        assertThat(result.items().get(0).canManage()).isFalse();
        verify(cupRepository, never()).findListItemsForTeams(any(), any(), any());
    }

    @Test
    void listAllCups_secondPageDoesNotRepeatTheFirst() {
        User coach = coach();
        TestHats.grant(coach, UserRole.ADMIN);
        when(userAccessService.isAdmin(coach)).thenReturn(true);
        var first = new CupListItem(UUID.randomUUID(), "A", null, coach.getId(), "A", "B", 0, 0);
        var second = new CupListItem(UUID.randomUUID(), "B", null, coach.getId(), "A", "B", 0, 0);
        var third = new CupListItem(UUID.randomUUID(), "C", null, coach.getId(), "A", "B", 0, 0);
        List<CupListItem> all = List.of(first, second, third);
        when(cupRepository.countListItems()).thenReturn(3L);
        when(cupRepository.findListItems(any())).thenAnswer(invocation -> {
            org.springframework.data.domain.Pageable pageable = invocation.getArgument(0);
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), all.size());
            return start >= all.size() ? List.of() : all.subList(start, end);
        });

        var pageOne = cupService.listAllCups(coach, UserRole.ADMIN, null, 1, 2);
        var pageTwo = cupService.listAllCups(coach, UserRole.ADMIN, null, 2, 2);

        assertThat(pageOne.items()).extracting(CupListItem::id).containsExactly(first.id(), second.id());
        assertThat(pageTwo.items()).extracting(CupListItem::id).containsExactly(third.id());
    }

    @Test
    void listAllCups_adminTeamChipUsesOnlyThatTeam() {
        User admin = coach();
        TestHats.grant(admin, UserRole.ADMIN);
        when(userAccessService.isAdmin(admin)).thenReturn(true);
        UUID teamId = UUID.randomUUID();
        UUID cupId = UUID.randomUUID();
        var item = new CupListItem(cupId, "Youth", null, admin.getId(), "Ada", "Coach", 1, 0);
        when(cupRepository.findListItemsContainingTeams(eq(Set.of(teamId)), any())).thenReturn(List.of(item));
        when(cupRepository.countListItemsContainingTeams(Set.of(teamId))).thenReturn(1L);

        var result = cupService.listAllCups(admin, UserRole.ADMIN, List.of(teamId), 1, 20);

        assertThat(result.items()).extracting(CupListItem::id).containsExactly(cupId);
        verify(cupRepository, never()).findListItems(any());
    }

    @Test
    @DisplayName("CU2: users.role = ADMIN without the admin hat does not see every cup")
    void listAllCups_adminRoleWithoutAdminHatIsNotAdmin() {
        User coach = coach();
        TestHats.grant(coach, UserRole.ADMIN);
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(userAccessService.getAccessibleTeamIds(coach)).thenReturn(Set.of());
        when(cupRepository.findListItemsCreatedBy(eq(coach.getId()), any())).thenReturn(List.of());
        when(cupRepository.countListItemsCreatedBy(coach.getId())).thenReturn(0L);

        cupService.listAllCups(coach, null, null, 1, 20);

        verify(cupRepository, never()).findListItems(any());
    }

    // ===== View and manage (CU2, 1.4) =====

    @Test
    void getCup_stripsCreatorEmail() {
        User coach = coach();
        Cup cup = cupOwnedBy(coach);
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(coach)).thenReturn(false);

        CupResponse response = cupService.getCup(cup.getId(), coach);

        assertThat(response.createdBy().email()).isNull();
        assertThat(response.createdBy().firstName()).isEqualTo("Chris");
        assertThat(response.createdBy().lastName()).isEqualTo("Coach");
        assertThat(response.canManage()).isTrue();
    }

    @Test
    void getCup_hidesRealMatchIdWhenViewerCannotAccessMatch() {
        User coach = coach();
        Cup cup = cupOwnedBy(coach);
        Match realMatch = new Match();
        realMatch.setId(UUID.randomUUID());
        CupMatch cupMatch = match(cup, team(cup, "Home", null), team(cup, "Away", null), TIME);
        cupMatch.setRealMatch(realMatch);
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(userAccessService.accessibleMatchIds(coach, List.of(realMatch.getId()))).thenReturn(Set.of());

        CupResponse response = cupService.getCup(cup.getId(), coach);

        assertThat(response.matches()).hasSize(1);
        assertThat(response.matches().get(0).realMatchId()).isNull();
    }

    @Test
    void getCup_forbiddenForUnrelatedUser() {
        Cup cup = cupOwnedBy(coach());
        User stranger = coach();
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(stranger)).thenReturn(false);
        when(userAccessService.getAccessibleTeamIds(stranger)).thenReturn(Set.of());

        assertThatThrownBy(() -> cupService.getCup(cup.getId(), stranger)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("CU2: an admin in the coach view does not see a foreign cup")
    void getCup_adminInCoachViewIsNotAdmin() {
        Cup cup = cupOwnedBy(coach());
        User admin = coach();
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.getAccessibleTeamIds(admin, UserRole.COACH)).thenReturn(Set.of());

        assertThatThrownBy(() -> cupService.getCup(cup.getId(), admin, UserRole.COACH)).isInstanceOf(ForbiddenException.class);
        verify(userAccessService, never()).isAdmin(admin);
    }

    @Test
    @DisplayName("1.4: a viewer whose team plays in the cup sees it but cannot manage it; the own team is flagged")
    void getCup_teamMemberSeesButCannotManage() {
        Cup cup = cupOwnedBy(coach());
        Team realTeam = realTeam();
        CupTeam own = team(cup, "FTC U11", realTeam);
        CupTeam other = team(cup, "MTK U11", null);
        User player = coach();
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.getAccessibleTeamIds(player, UserRole.PLAYER)).thenReturn(Set.of(realTeam.getId()));

        CupResponse response = cupService.getCup(cup.getId(), player, UserRole.PLAYER);

        assertThat(response.canManage()).isFalse();
        assertThat(response.teams()).filteredOn(t -> t.id().equals(own.getId())).singleElement()
                .satisfies(t -> assertThat(t.ownTeam()).isTrue());
        assertThat(response.teams()).filteredOn(t -> t.id().equals(other.getId())).singleElement()
                .satisfies(t -> assertThat(t.ownTeam()).isFalse());
    }

    @Test
    @DisplayName("1.2: a cup whose creator was deleted is managed by the admin only")
    void canManage_withoutCreatorOnlyAdmin() {
        Cup cup = cupOwnedBy(coach());
        cup.setCreatedBy(null);
        User admin = coach();
        User someone = coach();
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(admin)).thenReturn(true);
        when(userAccessService.isAdmin(someone)).thenReturn(false);

        assertThat(cupService.getCup(cup.getId(), admin, UserRole.ADMIN).canManage()).isTrue();
        assertThatThrownBy(() -> cupService.renameCup(cup.getId(),
                new com.example.footballanalysis.model.requests.UpdateCupRequest("X"), someone))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("CU9: matches come back by time, whatever order they were added in")
    void getCup_matchesAreOrderedByTime() {
        User coach = coach();
        Cup cup = cupOwnedBy(coach);
        CupTeam a = team(cup, "A", null);
        CupTeam b = team(cup, "B", null);
        CupTeam c = team(cup, "C", null);
        CupMatch late = match(cup, a, b, TIME.plusHours(2));
        CupMatch early = match(cup, b, c, TIME);
        CupMatch middle = match(cup, a, c, TIME.plusHours(1));
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(coach)).thenReturn(false);

        CupResponse response = cupService.getCup(cup.getId(), coach);

        assertThat(response.matches()).extracting(CupMatchResponse::id)
                .containsExactly(early.getId(), middle.getId(), late.getId());
    }

    // ===== Create (CU8) =====

    @Test
    void createCup_trimsTheName() {
        User coach = coach();
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(userAccessService.hasHat(coach, UserRole.COACH)).thenReturn(true);
        when(cupRepository.save(any(Cup.class))).thenAnswer(invocation -> {
            Cup saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        CupResponse response = cupService.createCup(new CreateCupRequest("  Őszi Kupa "), coach);

        assertThat(response.name()).isEqualTo("Őszi Kupa");
    }

    @Test
    void createCup_playerHatIsForbidden() {
        User player = coach();
        when(userAccessService.isAdmin(player)).thenReturn(false);
        when(userAccessService.hasHat(player, UserRole.COACH)).thenReturn(false);
        when(userAccessService.hasHat(player, UserRole.CLUB_ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> cupService.createCup(new CreateCupRequest("Kupa"), player))
                .isInstanceOf(ForbiddenException.class);
        verify(cupRepository, never()).save(any());
    }

    // ===== Teams (CU3, CU12) =====

    @Test
    @DisplayName("CU3: a foreign real team cannot be linked")
    void addTeam_foreignRealTeamIsForbidden() {
        User coach = coach();
        Cup cup = cupOwnedBy(coach);
        Team foreign = realTeam();
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(teamRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));
        when(userAccessService.isTeamStaff(coach, foreign)).thenReturn(false);

        assertThatThrownBy(() -> cupService.addTeamToCup(cup.getId(), new CupTeamCreateRequest("X", foreign.getId()), coach))
                .isInstanceOf(ForbiddenException.class);
        verify(cupTeamRepository, never()).save(any());
    }

    @Test
    void addTeam_ownRealTeamIsLinked() {
        User coach = coach();
        Cup cup = cupOwnedBy(coach);
        Team own = realTeam();
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(teamRepository.findById(own.getId())).thenReturn(Optional.of(own));
        when(userAccessService.isTeamStaff(coach, own)).thenReturn(true);
        when(cupTeamRepository.findSameName(cup.getId(), "FTC U11")).thenReturn(List.of());
        when(cupTeamRepository.findByCup_IdAndRealTeam_Id(cup.getId(), own.getId())).thenReturn(List.of());
        when(cupTeamRepository.save(any(CupTeam.class))).thenAnswer(invocation -> {
            CupTeam saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var response = cupService.addTeamToCup(cup.getId(), new CupTeamCreateRequest(" FTC U11 ", own.getId()), coach);

        assertThat(response.name()).isEqualTo("FTC U11");
        assertThat(response.realTeam().id()).isEqualTo(own.getId());
    }

    @Test
    @DisplayName("CU3: keeping the current link needs no team check")
    void updateTeam_keepingTheLinkIsAllowed() {
        User coach = coach();
        Cup cup = cupOwnedBy(coach);
        Team linked = realTeam();
        CupTeam team = team(cup, "FTC U11", linked);
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(cupTeamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(cupTeamRepository.findSameName(cup.getId(), "FTC U11 kék")).thenReturn(List.of());
        when(cupTeamRepository.findByCup_IdAndRealTeam_Id(cup.getId(), linked.getId())).thenReturn(List.of(team));
        when(cupTeamRepository.save(team)).thenReturn(team);

        var response = cupService.updateTeam(cup.getId(), team.getId(), new CupTeamCreateRequest("FTC U11 kék", linked.getId()), coach);

        assertThat(response.name()).isEqualTo("FTC U11 kék");
        verify(userAccessService, never()).isTeamStaff(any(), any());
    }

    @Test
    @DisplayName("CU12: the same name (any case, any spaces) is refused with 409")
    void addTeam_sameNameIsConflict() {
        User coach = coach();
        Cup cup = cupOwnedBy(coach);
        CupTeam existing = team(cup, "FTC U11", null);
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(cupTeamRepository.findSameName(cup.getId(), "ftc u11")).thenReturn(List.of(existing));

        assertThatThrownBy(() -> cupService.addTeamToCup(cup.getId(), new CupTeamCreateRequest(" ftc u11 ", null), coach))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("CU12: keeping the team's own name while editing is fine")
    void updateTeam_ownNameIsNotAConflict() {
        User coach = coach();
        Cup cup = cupOwnedBy(coach);
        CupTeam team = team(cup, "FTC U11", null);
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(cupTeamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(cupTeamRepository.findSameName(cup.getId(), "FTC U11")).thenReturn(List.of(team));
        when(cupTeamRepository.save(team)).thenReturn(team);

        assertThat(cupService.updateTeam(cup.getId(), team.getId(), new CupTeamCreateRequest("FTC U11", null), coach).name())
                .isEqualTo("FTC U11");
    }

    // ===== Matches (CU7, 2.7) =====

    @Test
    void scheduleMatch_rejectsTeamsFromAnotherCup() {
        User coach = coach();
        Cup cup = cupOwnedBy(coach);
        Cup otherCup = cupOwnedBy(coach);
        CupTeam home = team(cup, "Home", null);
        CupTeam away = team(otherCup, "Away", null);
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(cupTeamRepository.findById(home.getId())).thenReturn(Optional.of(home));
        when(cupTeamRepository.findById(away.getId())).thenReturn(Optional.of(away));

        assertThatThrownBy(() -> cupService.scheduleMatch(cup.getId(), new CupMatchScheduleRequest(home.getId(), away.getId(), TIME), coach))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("CU7 + 2.7: B–A next to A–B becomes the return match (leg 2)")
    void scheduleMatch_reversedPairingIsTheReturnMatch() {
        User coach = coach();
        Cup cup = cupOwnedBy(coach);
        CupTeam a = team(cup, "A", null);
        CupTeam b = team(cup, "B", null);
        CupMatch first = match(cup, a, b, TIME);
        stubSchedule(coach, cup, a, b, List.of(first));

        var response = cupService.scheduleMatch(cup.getId(), new CupMatchScheduleRequest(b.getId(), a.getId(), TIME.plusDays(7)), coach);

        assertThat(response.leg()).isEqualTo(2);
    }

    @Test
    @DisplayName("CU7: a third match of the same pairing is refused with 409")
    void scheduleMatch_thirdMatchIsConflict() {
        User coach = coach();
        Cup cup = cupOwnedBy(coach);
        CupTeam a = team(cup, "A", null);
        CupTeam b = team(cup, "B", null);
        CupMatch first = match(cup, a, b, TIME);
        CupMatch second = match(cup, b, a, TIME.plusDays(7));
        second.setLeg((short) 2);
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(cupTeamRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(cupTeamRepository.findById(b.getId())).thenReturn(Optional.of(b));
        when(cupMatchRepository.findPairing(cup.getId(), a.getId(), b.getId())).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> cupService.scheduleMatch(cup.getId(), new CupMatchScheduleRequest(a.getId(), b.getId(), TIME), coach))
                .isInstanceOf(ConflictException.class);
        verify(cupMatchRepository, never()).save(any());
    }

    @Test
    @DisplayName("1.3: a past time is accepted")
    void scheduleMatch_pastTimeIsAccepted() {
        User coach = coach();
        Cup cup = cupOwnedBy(coach);
        CupTeam a = team(cup, "A", null);
        CupTeam b = team(cup, "B", null);
        stubSchedule(coach, cup, a, b, List.of());
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);

        var response = cupService.scheduleMatch(cup.getId(), new CupMatchScheduleRequest(a.getId(), b.getId(), yesterday), coach);

        assertThat(response.scheduledAt()).isEqualTo(yesterday);
        assertThat(response.leg()).isEqualTo(1);
    }

    @Test
    @DisplayName("CU7: editing a match without changing the pairing keeps its leg and does not clash with itself")
    void updateMatch_samePairingKeepsLeg() {
        User coach = coach();
        Cup cup = cupOwnedBy(coach);
        CupTeam a = team(cup, "A", null);
        CupTeam b = team(cup, "B", null);
        CupMatch returnMatch = match(cup, b, a, TIME);
        returnMatch.setLeg((short) 2);
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(cupMatchRepository.findByIdAndCup_Id(returnMatch.getId(), cup.getId())).thenReturn(Optional.of(returnMatch));
        when(cupTeamRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(cupTeamRepository.findById(b.getId())).thenReturn(Optional.of(b));
        when(cupMatchRepository.save(returnMatch)).thenReturn(returnMatch);

        var response = cupService.updateMatch(cup.getId(), returnMatch.getId(),
                new CupMatchScheduleRequest(a.getId(), b.getId(), TIME.minusDays(3)), coach);

        assertThat(response.leg()).isEqualTo(2);
        verify(cupMatchRepository, never()).findPairing(any(), any(), any());
    }

    // ===== Score (2.5) =====

    @Test
    void clearScore_resetsAPlayedMatch() {
        User coach = coach();
        Cup cup = cupOwnedBy(coach);
        CupMatch played = match(cup, team(cup, "A", null), team(cup, "B", null), TIME);
        played.setPlayed(true);
        played.setHomeScore(2);
        played.setAwayScore(1);
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(cupMatchRepository.findByIdAndCup_Id(played.getId(), cup.getId())).thenReturn(Optional.of(played));

        cupService.clearScore(cup.getId(), played.getId(), coach);

        assertThat(played.isPlayed()).isFalse();
        assertThat(played.getHomeScore()).isNull();
        assertThat(played.getAwayScore()).isNull();
        verify(cupMatchRepository).save(played);
    }

    @Test
    @DisplayName("D2: clearing a match with no result changes nothing and raises no error")
    void clearScore_onUnplayedMatchIsNoop() {
        User coach = coach();
        Cup cup = cupOwnedBy(coach);
        CupMatch unplayed = match(cup, team(cup, "A", null), team(cup, "B", null), TIME);
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(cupMatchRepository.findByIdAndCup_Id(unplayed.getId(), cup.getId())).thenReturn(Optional.of(unplayed));

        cupService.clearScore(cup.getId(), unplayed.getId(), coach);

        verify(cupMatchRepository, never()).save(any());
        verify(auditEventService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void clearScore_nonManagerIsForbidden() {
        Cup cup = cupOwnedBy(coach());
        User stranger = coach();
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(stranger)).thenReturn(false);

        assertThatThrownBy(() -> cupService.clearScore(cup.getId(), UUID.randomUUID(), stranger))
                .isInstanceOf(ForbiddenException.class);
    }

    // ===== Delete (2.6, D7) =====

    @Test
    @DisplayName("D7: the creator cannot delete a cup with played matches")
    void deleteCup_creatorWithPlayedMatchesIsConflict() {
        User coach = coach();
        Cup cup = cupOwnedBy(coach);
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(cupMatchRepository.existsByCup_IdAndPlayedTrue(cup.getId())).thenReturn(true);

        assertThatThrownBy(() -> cupService.deleteCup(cup.getId(), coach)).isInstanceOf(ConflictException.class);
        verify(cupRepository, never()).delete(any());
    }

    @Test
    void deleteCup_adminDeletesEvenWithPlayedMatches() {
        Cup cup = cupOwnedBy(coach());
        User admin = coach();
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(admin)).thenReturn(true);

        cupService.deleteCup(cup.getId(), admin);

        verify(cupRepository).delete(cup);
        verify(cupMatchRepository, never()).existsByCup_IdAndPlayedTrue(any());
    }

    // ===== Video link (CU5) =====

    @Test
    @DisplayName("CU5: a cup match with no real team cannot get a video")
    void linkRealMatch_withoutRealTeamIsBadRequest() {
        User coach = coach();
        Cup cup = cupOwnedBy(coach);
        CupMatch cupMatch = match(cup, team(cup, "A", null), team(cup, "B", null), TIME);
        stubLink(coach, cup, cupMatch);

        assertThatThrownBy(() -> cupService.linkRealMatch(cup.getId(), cupMatch.getId(), UUID.randomUUID(), coach))
                .isInstanceOf(BadRequestException.class);
        verify(matchRepository, never()).findById(any());
    }

    @Test
    @DisplayName("CU5: a match of another team cannot be linked")
    void linkRealMatch_otherTeamsMatchIsBadRequest() {
        User coach = coach();
        Cup cup = cupOwnedBy(coach);
        CupMatch cupMatch = match(cup, team(cup, "FTC", realTeam()), team(cup, "B", null), TIME);
        Match foreignMatch = new Match();
        foreignMatch.setId(UUID.randomUUID());
        foreignMatch.setHomeTeam(realTeam());
        stubLink(coach, cup, cupMatch);
        when(matchRepository.findById(foreignMatch.getId())).thenReturn(Optional.of(foreignMatch));

        assertThatThrownBy(() -> cupService.linkRealMatch(cup.getId(), cupMatch.getId(), foreignMatch.getId(), coach))
                .isInstanceOf(BadRequestException.class);
        verify(cupMatchRepository, never()).save(any());
    }

    @Test
    void linkRealMatch_matchOfTheCupTeamIsLinked() {
        User coach = coach();
        Cup cup = cupOwnedBy(coach);
        Team ftc = realTeam();
        CupMatch cupMatch = match(cup, team(cup, "B", null), team(cup, "FTC", ftc), TIME);
        Match video = new Match();
        video.setId(UUID.randomUUID());
        video.setHomeTeam(ftc);
        stubLink(coach, cup, cupMatch);
        when(matchRepository.findById(video.getId())).thenReturn(Optional.of(video));
        when(cupMatchRepository.save(cupMatch)).thenReturn(cupMatch);
        when(userAccessService.accessibleMatchIds(coach, List.of(video.getId()))).thenReturn(Set.of(video.getId()));

        var response = cupService.linkRealMatch(cup.getId(), cupMatch.getId(), video.getId(), coach);

        assertThat(response.realMatchId()).isEqualTo(video.getId());
    }

    @Test
    void linkRealMatch_viewerIsForbidden() {
        Cup cup = cupOwnedBy(coach());
        User viewer = coach();
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(viewer)).thenReturn(false);

        assertThatThrownBy(() -> cupService.linkRealMatch(cup.getId(), UUID.randomUUID(), UUID.randomUUID(), viewer))
                .isInstanceOf(ForbiddenException.class);
    }

    // ===== Helpers =====

    private void stubSchedule(User coach, Cup cup, CupTeam a, CupTeam b, List<CupMatch> pairing) {
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(cupTeamRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(cupTeamRepository.findById(b.getId())).thenReturn(Optional.of(b));
        when(cupMatchRepository.findPairing(eq(cup.getId()), any(), any())).thenReturn(pairing);
        when(cupMatchRepository.save(any(CupMatch.class))).thenAnswer(invocation -> {
            CupMatch saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    private void stubLink(User coach, Cup cup, CupMatch cupMatch) {
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(cupMatchRepository.findByIdAndCup_Id(cupMatch.getId(), cup.getId())).thenReturn(Optional.of(cupMatch));
    }

    private static User coach() {
        User coach = TestHats.user(UserRole.COACH);
        coach.setId(UUID.randomUUID());
        coach.setEmail("secret@example.com");
        coach.setFirstName("Chris");
        coach.setLastName("Coach");
        TestHats.grant(coach, UserRole.COACH);
        return coach;
    }

    private static Cup cupOwnedBy(User coach) {
        Cup cup = new Cup();
        cup.setId(UUID.randomUUID());
        cup.setName("Spring Cup");
        cup.setCreatedBy(coach);
        cup.setTeams(new HashSet<>());
        cup.setMatches(new HashSet<>());
        return cup;
    }

    private static Team realTeam() {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setName("Real " + team.getId());
        return team;
    }

    private static CupTeam team(Cup cup, String name, Team realTeam) {
        CupTeam team = new CupTeam();
        team.setId(UUID.randomUUID());
        team.setName(name);
        team.setCup(cup);
        team.setRealTeam(realTeam);
        cup.getTeams().add(team);
        return team;
    }

    private static CupMatch match(Cup cup, CupTeam home, CupTeam away, LocalDateTime at) {
        CupMatch match = new CupMatch();
        match.setId(UUID.randomUUID());
        match.setCup(cup);
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setScheduledAt(at);
        cup.getMatches().add(match);
        return match;
    }
}
