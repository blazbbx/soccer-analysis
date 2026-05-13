package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.user.*;
import com.example.footballanalysis.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAccessService {

    private final UserRepository userRepository;
    private final CoachRepository coachRepository;
    private final PlayerRepository playerRepository;
    private final FanRepository fanRepository;
    private final MatchRepository matchRepository;
    private final ClipRepository clipRepository;
    private final TeamRepository teamRepository;

    public User resolveCurrentUser(Jwt jwt) {
        if (jwt == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required to access this resource.");
        }

        return resolveUserBySubject(jwt.getSubject())
                .or(() -> userRepository.findByEmail(resolveEmail(jwt)))
                .orElseThrow(() -> new NotFoundException(
                        "error.user.not_found",
                        new Object[]{resolveLookupValue(jwt)},
                        "User not found for authenticated user: " + resolveLookupValue(jwt)));
    }

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

    public Set<UUID> getAccessibleTeamIds(User actor) {
        if (actor == null || actor.getId() == null) {
            return Set.of();
        }

        if (actor.getRole() == UserRole.ADMIN) {
            return new LinkedHashSet<>(teamRepository.findAllTeamIds());
        }

        UUID userId = actor.getId();

        if (actor instanceof Coach) {
            return new LinkedHashSet<>(coachRepository.findTeamIdsByCoachId(userId));
        }
        if (actor instanceof Player) {
            return new LinkedHashSet<>(playerRepository.findTeamIdsByPlayerId(userId));
        }

        return Set.of();
    }

    private Optional<User> resolveUserBySubject(String subject) {
        if (subject == null || subject.isBlank()) {
            return Optional.empty();
        }

        try {
            return userRepository.findById(UUID.fromString(subject));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String resolveEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            email = jwt.getClaimAsString("preferred_username");
        }
        if (email == null || email.isBlank()) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authenticated token does not contain an email.");
        }
        return email;
    }

    private String resolveLookupValue(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject != null && !subject.isBlank()) {
            return subject;
        }
        return resolveEmail(jwt);
    }






}
