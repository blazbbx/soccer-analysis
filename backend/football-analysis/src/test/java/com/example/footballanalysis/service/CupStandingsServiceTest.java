package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.Cup;
import com.example.footballanalysis.model.db.CupMatch;
import com.example.footballanalysis.model.db.CupTeam;
import com.example.footballanalysis.model.responses.CupStandingsRowResponse;
import com.example.footballanalysis.repository.CupMatchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CupStandingsService")
class CupStandingsServiceTest {

    @Mock private CupMatchRepository cupMatchRepository;

    @InjectMocks
    private CupStandingsService standingsService;

    private final UUID cupId = UUID.randomUUID();

    @Test
    @DisplayName("1.5: full tie is ordered by name, ignoring case")
    void fullTieIsOrderedByName() {
        CupTeam zeta = team("zeta");
        CupTeam alfa = team("Alfa");
        CupTeam beta = team("beta");
        List<CupTeam> teams = List.of(zeta, alfa, beta);
        when(cupMatchRepository.findByCup_IdAndPlayedTrue(cupId)).thenReturn(List.of());

        List<CupStandingsRowResponse> rows = standingsService.calculateStandings(cupId, teams, Set.of());

        assertThat(rows).extracting(CupStandingsRowResponse::teamName).containsExactly("Alfa", "beta", "zeta");
    }

    @Test
    void pointsThenGoalDifferenceThenGoalsFor() {
        CupTeam a = team("A");
        CupTeam b = team("B");
        CupTeam c = team("C");
        // A beats C 3-0 (A: 3 pts, +3), B beats C 4-2 (B: 3 pts, +2), so A is above B.
        List<CupTeam> teams = List.of(c, b, a);
        when(cupMatchRepository.findByCup_IdAndPlayedTrue(cupId))
                .thenReturn(List.of(played(a, c, 3, 0), played(b, c, 4, 2)));

        List<CupStandingsRowResponse> rows = standingsService.calculateStandings(cupId, teams, Set.of());

        assertThat(rows).extracting(CupStandingsRowResponse::teamName).containsExactly("A", "B", "C");
        assertThat(rows.get(0).points()).isEqualTo(3);
        assertThat(rows.get(2).losses()).isEqualTo(2);
    }

    @Test
    @DisplayName("3 + 2.8: the caller's teams are flagged")
    void ownTeamsAreFlagged() {
        CupTeam own = team("Own");
        CupTeam other = team("Other");
        List<CupTeam> teams = List.of(own, other);
        when(cupMatchRepository.findByCup_IdAndPlayedTrue(cupId)).thenReturn(List.of());

        List<CupStandingsRowResponse> rows = standingsService.calculateStandings(cupId, teams, Set.of(own.getId()));

        assertThat(rows).filteredOn(CupStandingsRowResponse::ownTeam).extracting(CupStandingsRowResponse::teamId)
                .containsExactly(own.getId());
    }

    @Test
    @DisplayName("CU6: after a team swap the result belongs to the new team, not the old one")
    void resultFollowsTheTeamOnTheMatch() {
        CupTeam ftc = team("FTC");
        CupTeam mtk = team("MTK");
        CupTeam vasas = team("Vasas");
        // "FTC – MTK 2–1" was edited to "FTC – Vasas 2–1": the stored match now has Vasas.
        List<CupTeam> teams = List.of(ftc, mtk, vasas);
        when(cupMatchRepository.findByCup_IdAndPlayedTrue(cupId)).thenReturn(List.of(played(ftc, vasas, 2, 1)));

        List<CupStandingsRowResponse> rows = standingsService.calculateStandings(cupId, teams, Set.of());

        assertThat(row(rows, vasas).losses()).isEqualTo(1);
        assertThat(row(rows, mtk).played()).isZero();
    }

    private static CupStandingsRowResponse row(List<CupStandingsRowResponse> rows, CupTeam team) {
        return rows.stream().filter(r -> r.teamId().equals(team.getId())).findFirst().orElseThrow();
    }

    private CupTeam team(String name) {
        CupTeam team = new CupTeam();
        team.setId(UUID.randomUUID());
        team.setName(name);
        Cup cup = new Cup();
        cup.setId(cupId);
        team.setCup(cup);
        return team;
    }

    private static CupMatch played(CupTeam home, CupTeam away, int homeScore, int awayScore) {
        CupMatch match = new CupMatch();
        match.setId(UUID.randomUUID());
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        match.setPlayed(true);
        return match;
    }
}
