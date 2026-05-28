package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.CupMatch;
import com.example.footballanalysis.model.db.CupTeam;
import com.example.footballanalysis.model.responses.CupStandingsRowResponse;
import com.example.footballanalysis.repository.CupMatchRepository;
import com.example.footballanalysis.repository.CupTeamRepository;
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

    private final CupMatchRepository cupMatchRepository;
    private final CupTeamRepository cupTeamRepository;

    /**
     * Dinamikus standings kiszámítása a Cup-hoz a lejátszott mérkőzések alapján.
     * A standings futásidőben kerül kiszámításra, nem tárolva.
     *
     * Sorrend: Pontszám (DESC) -> Gólkülönbség (DESC) -> Rúgott gól (DESC)
     */
    @Transactional(readOnly = true)
    public List<CupStandingsRowResponse> calculateStandings(UUID cupId) {
        // Összes csapat a kupában
        List<CupTeam> allTeams = cupTeamRepository.findByCup_IdOrderByCreatedAt(cupId);

        // Csapatonként inicializálunk egy táblaelemet
        Map<UUID, StandingsRowAccumulator> standingsMap = allTeams.stream()
                .collect(Collectors.toMap(
                        CupTeam::getId,
                        team -> new StandingsRowAccumulator(team.getId(), team.getName())
                ));

        // Lejátszott mérkőzéseket feldolgozzuk
        cupMatchRepository.streamByCup_IdAndPlayedTrue(cupId)
                .forEach(match -> aggregateMatchResult(standingsMap, match));

        // Konvertálás a response objektummá és rendezés
        return standingsMap.values().stream()
                .map(StandingsRowAccumulator::toResponse)
                .sorted(this::compareStandingsRows)
                .toList();
    }

    /**
     * Egy team standings sorának kiszámítása a Cup-ban.
     */
    @Transactional(readOnly = true)
    public CupStandingsRowResponse getTeamStandings(UUID cupId, UUID teamId) {
        Optional<CupTeam> teamOpt = cupTeamRepository.findById(teamId);
        if (teamOpt.isEmpty()) {
            return null;
        }

        CupTeam team = teamOpt.get();
        StandingsRowAccumulator accumulator = new StandingsRowAccumulator(team.getId(), team.getName());

        // Összes lejátszott meccs a kupában
        cupMatchRepository.streamByCup_IdAndPlayedTrue(cupId)
                .filter(match -> match.getHomeTeam().getId().equals(teamId)
                        || match.getAwayTeam().getId().equals(teamId))
                .forEach(match -> aggregateMatchResult(Map.of(teamId, accumulator), match));

        return accumulator.toResponse();
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
     * Standings összehasolítási logika: Pontszám -> Gólkülönbség -> Rúgott gól
     */
    private int compareStandingsRows(CupStandingsRowResponse row1, CupStandingsRowResponse row2) {
        // Pontszám (csökkenő)
        int pointsCmp = row2.points().compareTo(row1.points());
        if (pointsCmp != 0) return pointsCmp;

        // Gólkülönbség (csökkenő)
        int goalDiffCmp = row2.goalDifference().compareTo(row1.goalDifference());
        if (goalDiffCmp != 0) return goalDiffCmp;

        // Rúgott gólok (csökkenő)
        return row2.goalsFor().compareTo(row1.goalsFor());
    }

    /**
     * Belső segédosztály a standings agregáció kezelésére.
     */
    public static class StandingsRowAccumulator {
        private final UUID teamId;
        private final String teamName;
        private int played = 0;
        private int wins = 0;
        private int draws = 0;
        private int losses = 0;
        private int goalsFor = 0;
        private int goalsAgainst = 0;

        public StandingsRowAccumulator(UUID teamId, String teamName) {
            this.teamId = teamId;
            this.teamName = teamName;
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
                    getPoints()
            );
        }
    }
}


