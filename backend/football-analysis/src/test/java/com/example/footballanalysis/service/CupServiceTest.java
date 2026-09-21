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
        assertThat(cupService.listAllCups(null)).isEmpty();
    }

    @Test
    void listAllCups_returnsCreatedCupsWhenActorHasNoTeams() {
        Coach coach = coach();
        Cup cup = cupOwnedBy(coach);
        when(userAccessService.isAdmin(coach)).thenReturn(false);
        when(userAccessService.getAccessibleTeamIds(coach)).thenReturn(Set.of());
        when(cupRepository.findByCreatedBy_IdOrderByCreatedAtDesc(coach.getId())).thenReturn(List.of(cup));

        List<CupResponse> result = cupService.listAllCups(coach);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(cup.getId());
        assertThat(result.get(0).createdBy().email()).isNull();
        assertThat(result.get(0).createdBy().id()).isEqualTo(coach.getId());
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
