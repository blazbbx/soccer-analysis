package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.model.db.Cup;
import com.example.footballanalysis.model.db.CupMatch;
import com.example.footballanalysis.model.db.CupTeam;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.CupMatchScheduleRequest;
import com.example.footballanalysis.model.responses.CupResponse;
import com.example.footballanalysis.repository.CupMatchRepository;
import com.example.footballanalysis.repository.CupRepository;
import com.example.footballanalysis.repository.CupTeamRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.TeamRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
@DisplayName("CupService visibility and PII")
class CupServiceTest {

    @Mock private CupRepository cupRepository;
    @Mock private CupTeamRepository cupTeamRepository;
    @Mock private CupMatchRepository cupMatchRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private AuditEventService auditEventService;
    @Mock private UserAccessService userAccessService;

    @InjectMocks
    private CupService cupService;

    @Test
    void listAllCups_returnsEmptyWhenActorMissing() {
        assertThat(cupService.listAllCups(null, null, null, null, null).items()).isEmpty();
    }

    @Test
    void listAllCups_returnsCreatedCupsWhenActorHasNoTeams() {
        Coach coach = coach();
        Cup cup = cupOwnedBy(coach);
        var item = new com.example.footballanalysis.model.responses.CupListItem(
                cup.getId(), cup.getName(), cup.getCreatedAt(), coach.getId(), "Ada", "Coach", 1, 0);
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(userAccessService.getAccessibleTeamIds(coach)).thenReturn(Set.of());
        when(cupRepository.findListItemsCreatedBy(eq(coach.getId()), any())).thenReturn(List.of(item));
        when(cupRepository.countListItemsCreatedBy(coach.getId())).thenReturn(1L);

        var result = cupService.listAllCups(coach, null, null, null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo(cup.getId());
        assertThat(result.items().get(0).createdById()).isEqualTo(coach.getId());
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void listAllCups_usesTheRequestedChildTeams() {
        Coach coach = coach();
        UUID parentTeamId = UUID.randomUUID();
        UUID cupId = UUID.randomUUID();
        var item = new com.example.footballanalysis.model.responses.CupListItem(
                cupId, "Youth", null, coach.getId(), "Ada", "Coach", 2, 1);
        when(userAccessService.getAccessibleTeamIds(coach, UserRole.PARENT)).thenReturn(Set.of(parentTeamId));
        when(cupRepository.findListItemsContainingTeams(eq(Set.of(parentTeamId)), any()))
                .thenReturn(List.of(item));
        when(cupRepository.countListItemsContainingTeams(Set.of(parentTeamId))).thenReturn(1L);

        var result = cupService.listAllCups(coach, UserRole.PARENT, List.of(parentTeamId), 1, 20);

        assertThat(result.items()).extracting(com.example.footballanalysis.model.responses.CupListItem::id).containsExactly(cupId);
        verify(cupRepository, never()).findListItemsForTeams(any(), any(), any());
    }

    @Test
    void listAllCups_secondPageDoesNotRepeatTheFirst() {
        Coach coach = coach();
        coach.setRole(UserRole.ADMIN);
        when(userAccessService.isAdmin(coach)).thenReturn(true);
        var first = new com.example.footballanalysis.model.responses.CupListItem(
                UUID.randomUUID(), "A", null, coach.getId(), "A", "B", 0, 0);
        var second = new com.example.footballanalysis.model.responses.CupListItem(
                UUID.randomUUID(), "B", null, coach.getId(), "A", "B", 0, 0);
        var third = new com.example.footballanalysis.model.responses.CupListItem(
                UUID.randomUUID(), "C", null, coach.getId(), "A", "B", 0, 0);
        List<com.example.footballanalysis.model.responses.CupListItem> all = List.of(first, second, third);
        when(cupRepository.countListItems()).thenReturn(3L);
        when(cupRepository.findListItems(any())).thenAnswer(invocation -> {
            org.springframework.data.domain.Pageable pageable = invocation.getArgument(0);
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), all.size());
            return start >= all.size() ? List.of() : all.subList(start, end);
        });

        var pageOne = cupService.listAllCups(coach, UserRole.ADMIN, null, 1, 2);
        var pageTwo = cupService.listAllCups(coach, UserRole.ADMIN, null, 2, 2);

        assertThat(pageOne.items()).extracting(com.example.footballanalysis.model.responses.CupListItem::id)
                .containsExactly(first.id(), second.id());
        assertThat(pageTwo.items()).extracting(com.example.footballanalysis.model.responses.CupListItem::id)
                .containsExactly(third.id());
    }

    @Test
    void listAllCups_adminTeamChipUsesOnlyThatTeam() {
        Coach admin = coach();
        admin.setRole(UserRole.ADMIN);
        when(userAccessService.isAdmin(admin)).thenReturn(true);
        UUID teamId = UUID.randomUUID();
        UUID cupId = UUID.randomUUID();
        var item = new com.example.footballanalysis.model.responses.CupListItem(
                cupId, "Youth", null, admin.getId(), "Ada", "Coach", 1, 0);
        when(cupRepository.findListItemsContainingTeams(eq(Set.of(teamId)), any())).thenReturn(List.of(item));
        when(cupRepository.countListItemsContainingTeams(Set.of(teamId))).thenReturn(1L);

        var result = cupService.listAllCups(admin, UserRole.ADMIN, List.of(teamId), 1, 20);

        assertThat(result.items()).extracting(com.example.footballanalysis.model.responses.CupListItem::id)
                .containsExactly(cupId);
        verify(cupRepository, never()).findListItems(any());
    }

    @Test
    void getCup_stripsCreatorEmail() {
        Coach coach = coach();
        Cup cup = cupOwnedBy(coach);
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(coach)).thenReturn(false);

        CupResponse response = cupService.getCup(cup.getId(), coach);

        assertThat(response.createdBy().email()).isNull();
        assertThat(response.createdBy().firstName()).isEqualTo("Chris");
        assertThat(response.createdBy().lastName()).isEqualTo("Coach");
    }

    @Test
    void getCup_hidesRealMatchIdWhenViewerCannotAccessMatch() {
        Coach coach = coach();
        Coach other = coach();
        Cup cup = cupOwnedBy(coach);
        UUID realMatchId = UUID.randomUUID();
        Match realMatch = new Match();
        realMatch.setId(realMatchId);

        CupTeam home = new CupTeam();
        home.setId(UUID.randomUUID());
        home.setName("Home");
        home.setCup(cup);
        CupTeam away = new CupTeam();
        away.setId(UUID.randomUUID());
        away.setName("Away");
        away.setCup(cup);
        CupMatch cupMatch = new CupMatch();
        cupMatch.setId(UUID.randomUUID());
        cupMatch.setHomeTeam(home);
        cupMatch.setAwayTeam(away);
        cupMatch.setRealMatch(realMatch);
        cup.setMatches(new HashSet<>(List.of(cupMatch)));

        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(userAccessService.canAccessMatch(coach, realMatchId)).thenReturn(false);

        CupResponse response = cupService.getCup(cup.getId(), coach);

        assertThat(response.matches()).hasSize(1);
        assertThat(response.matches().iterator().next().realMatchId()).isNull();
        assertThat(other.getId()).isNotEqualTo(coach.getId());
    }

    @Test
    void getCup_forbiddenForUnrelatedUser() {
        Coach owner = coach();
        Coach stranger = coach();
        Cup cup = cupOwnedBy(owner);
        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(stranger)).thenReturn(false);
        when(userAccessService.getAccessibleTeamIds(stranger)).thenReturn(Set.of());

        assertThatThrownBy(() -> cupService.getCup(cup.getId(), stranger))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void scheduleMatch_rejectsTeamsFromAnotherCup() {
        Coach coach = coach();
        Cup cup = cupOwnedBy(coach);
        Cup otherCup = cupOwnedBy(coach);
        CupTeam home = new CupTeam();
        home.setId(UUID.randomUUID());
        home.setName("Home");
        home.setCup(otherCup);
        CupTeam away = new CupTeam();
        away.setId(UUID.randomUUID());
        away.setName("Away");
        away.setCup(cup);

        when(cupRepository.findById(cup.getId())).thenReturn(Optional.of(cup));
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(cupTeamRepository.findById(home.getId())).thenReturn(Optional.of(home));
        when(cupTeamRepository.findById(away.getId())).thenReturn(Optional.of(away));

        CupMatchScheduleRequest request = new CupMatchScheduleRequest(
                home.getId(),
                away.getId(),
                LocalDateTime.of(2026, 10, 1, 15, 0));

        assertThatThrownBy(() -> cupService.scheduleMatch(cup.getId(), request, coach))
                .isInstanceOf(BadRequestException.class);
    }

    private static Coach coach() {
        Coach coach = new Coach();
        coach.setId(UUID.randomUUID());
        coach.setEmail("secret@example.com");
        coach.setFirstName("Chris");
        coach.setLastName("Coach");
        coach.setRole(UserRole.COACH);
        return coach;
    }

    private static Cup cupOwnedBy(Coach coach) {
        Cup cup = new Cup();
        cup.setId(UUID.randomUUID());
        cup.setName("Spring Cup");
        cup.setCreatedBy(coach);
        cup.setTeams(new HashSet<>());
        cup.setMatches(new HashSet<>());
        return cup;
    }
}
