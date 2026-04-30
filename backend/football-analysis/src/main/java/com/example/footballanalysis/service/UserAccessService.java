package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.user.*;
import com.example.footballanalysis.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import java.util.Objects;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAccessService {

    private final CoachRepository coachRepository;
    private final PlayerRepository playerRepository;
    private final FanRepository fanRepository;
    private final MatchRepository matchRepository;
    private final ClipRepository clipRepository;

    public boolean hasTeamAccess(User actor, Collection<UUID> teamIds) {
        List<UUID> cleanTeamIds = teamIds.stream().filter(Objects::nonNull).toList();
        if (cleanTeamIds.isEmpty() || actor == null) return false;

        UUID userId = actor.getId();

        if (actor.getRole() == UserRole.ADMIN) {
            return true;
        }

        if (actor instanceof Coach) {
            return coachRepository.existsByIdAndTeams_IdIn(userId, cleanTeamIds);
        }
        if (actor instanceof Player) {
            return playerRepository.existsByIdAndTeams_IdIn(userId, cleanTeamIds);
        }
        if (actor instanceof Fan) {
            return fanRepository.existsByIdAndTeams_IdIn(userId, cleanTeamIds);
        }

        return false;
    }

    public boolean canAccessMatch(User actor, UUID matchId) {
        if (matchId == null || actor == null) return false;

        if (actor.getRole() == UserRole.ADMIN) {
            return true;
        }

        // 1. Megszerezzük a meccshez tartozó csapatok ID-it (Hazai és Vendég)
        // Ez a MatchRepository-dban lévő @Query-t hívja meg
        List<UUID> matchTeamIds = matchRepository.findTeamIdsByMatchId(matchId);

        // 2. Meghívjuk a központosított hasTeamAccess metódust.
        // Ez a metódus már tudja, hogy Coach, Player vagy Fan esetén
        // melyik repository-hoz kell fordulnia.
        return hasTeamAccess(actor, matchTeamIds);
    }

    public boolean canAccessClip(User actor, UUID clipId) {
        if (clipId == null || actor == null) return false;

        if (actor.getRole() == UserRole.ADMIN) {
            return true;
        }

        // Megkeressük, melyik meccshez tartozik a klip
        UUID matchId = clipRepository.findMatchIdByClipId(clipId);

        // Ha megvan a meccs, egyszerűen meghívjuk a canAccessMatch metódust (REUSE!)
        return canAccessMatch(actor, matchId);
    }






}
