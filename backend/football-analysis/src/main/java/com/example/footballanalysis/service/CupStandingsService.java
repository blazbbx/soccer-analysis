package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.CupMatch;
import com.example.footballanalysis.model.db.CupTeam;
import com.example.footballanalysis.model.responses.CupStandingsRowResponse;
import com.example.footballanalysis.repository.CupMatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CupStandingsService {

    /** Pont -> gólkülönbség -> rúgott gól (mind csökkenő) -> név -> azonosító. */
    static final Comparator<CupStandingsRowResponse> STANDINGS_ORDER = Comparator
            .comparing(CupStandingsRowResponse::points, Comparator.reverseOrder())
            .thenComparing(CupStandingsRowResponse::goalDifference, Comparator.reverseOrder())
            .thenComparing(CupStandingsRowResponse::goalsFor, Comparator.reverseOrder())
            .thenComparing(CupStandingsRowResponse::teamName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(CupStandingsRowResponse::teamId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final CupMatchRepository cupMatchRepository;

    /**
     * Dinamikus standings kiszámítása a Cup-hoz a lejátszott mérkőzések alapján.
     * A standings futásidőben kerül kiszámításra, nem tárolva.
     *
     * Sorrend: Pontszám (DESC) -> Gólkülönbség (DESC) -> Rúgott gól (DESC) -> Csapatnév (ABC, kis-nagybetű nélkül)
     * -> csapat-azonosító. Így teljes holtversenyben is mindig ugyanaz a sorrend; a helyezés a sor indexe.
     *
     * @param teams         a kupa összes csapata; a hívó már betöltötte (jogosultság-ellenőrzés), ezért nem kérjük le újra
     * @param ownCupTeamIds a hívó saját csapatai (kupacsapat-azonosítók), ezek sora {@code ownTeam = true}
     */
    @Transactional(readOnly = true)
    public List<CupStandingsRowResponse> calculateStandings(UUID cupId, Collection<CupTeam> teams, Set<UUID> ownCupTeamIds) {
        // Csapatonként inicializálunk egy táblaelemet
        Map<UUID, StandingsRowAccumulator> standingsMap = teams.stream()
                .collect(Collectors.toMap(
                        CupTeam::getId,
                        team -> new StandingsRowAccumulator(team.getId(), team.getName(), ownCupTeamIds.contains(team.getId()))
                ));

        // Lejátszott mérkőzéseket feldolgozzuk
        // Egy kupában néhány tucat meccs van: sima lista, nem kell lezárni, mint egy Streamet.
        cupMatchRepository.findByCup_IdAndPlayedTrue(cupId)
                .forEach(match -> aggregateMatchResult(standingsMap, match));

        // Konvertálás a response objektummá és rendezés
        return standingsMap.values().stream()
                .map(StandingsRowAccumulator::toResponse)
                .sorted(STANDINGS_ORDER)
                .toList();
    }

    /**
     * Egy mérkőzés eredményét aggregálja a tablaakkulátorokba.
     */
    private void aggregateMatchResult(Map<UUID, StandingsRowAccumulator> standingsMap, CupMatch match) {
        UUID homeTeamId = match.getHomeTeam().getId();
        UUID awayTeamId = match.getAwayTeam().getId();
        Integer homeScore = match.getHomeScore();
        Integer awayScore = match.getAwayScore();

        StandingsRowAccumulator homeAccum = standingsMap.get(homeTeamId);
        StandingsRowAccumulator awayAccum = standingsMap.get(awayTeamId);

        if (homeAccum == null || awayAccum == null) {
            log.warn("Team not found in standings map for match {}", match.getId());
            return;
        }

        // Mezők frissítése
        homeAccum.incrementPlayed();
        awayAccum.incrementPlayed();

        homeAccum.addGoalsFor(homeScore);
        homeAccum.addGoalsAgainst(awayScore);
        awayAccum.addGoalsFor(awayScore);
        awayAccum.addGoalsAgainst(homeScore);

        // Győzelem/döntetlen/vereség logika
        if (homeScore > awayScore) {
            homeAccum.incrementWins();
            awayAccum.incrementLosses();
        } else if (homeScore < awayScore) {
            awayAccum.incrementWins();
            homeAccum.incrementLosses();
        } else {
            homeAccum.incrementDraws();
            awayAccum.incrementDraws();
        }
    }

    /**
     * Belső segédosztály a standings agregáció kezelésére.
     */
    public static class StandingsRowAccumulator {
        private final UUID teamId;
        private final String teamName;
        private final boolean ownTeam;
        private int played = 0;
        private int wins = 0;
        private int draws = 0;
        private int losses = 0;
        private int goalsFor = 0;
        private int goalsAgainst = 0;

        public StandingsRowAccumulator(UUID teamId, String teamName, boolean ownTeam) {
            this.teamId = teamId;
            this.teamName = teamName;
            this.ownTeam = ownTeam;
        }

        public void incrementPlayed() { this.played++; }
        public void incrementWins() { this.wins++; }
        public void incrementDraws() { this.draws++; }
        public void incrementLosses() { this.losses++; }
        public void addGoalsFor(int goals) { this.goalsFor += goals; }
        public void addGoalsAgainst(int goals) { this.goalsAgainst += goals; }

        public int getPoints() {
            return wins * 3 + draws;
        }

        public int getGoalDifference() {
            return goalsFor - goalsAgainst;
        }

        public CupStandingsRowResponse toResponse() {
            return new CupStandingsRowResponse(
                    teamId,
                    teamName,
                    played,
                    wins,
                    draws,
                    losses,
                    goalsFor,
                    goalsAgainst,
                    getGoalDifference(),
                    getPoints(),
                    ownTeam
            );
        }
    }
}


