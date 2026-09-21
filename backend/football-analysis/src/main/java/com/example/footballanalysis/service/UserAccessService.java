package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.user.*;
import com.example.footballanalysis.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

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
    private final ClubRepository clubRepository;

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
        List<UUID> cleanTeamIds = teamIds == null
                ? List.of()
                : teamIds.stream().filter(Objects::nonNull).toList();
        if (cleanTeamIds.isEmpty() || actor == null) return false;

        UUID userId = actor.getId();

        if (isAdmin(actor)) {
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

    public void requireTeamAccess(User actor, Collection<UUID> teamIds) {
        if (!hasTeamAccess(actor, teamIds)) {
            throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied to team.");
        }
    }

    public boolean hasClubAccess(User actor, UUID clubId) {
        if (clubId == null || actor == null) return false;
        if (isAdmin(actor)) return true;
        var club = clubRepository.findById(clubId).orElse(null);
        if (club == null) return false;
        if (actor.getId() != null && actor.getId().equals(club.getCreatedByUserId())) {
            return true;
        }
        List<UUID> teamIds = teamRepository.findByClub_Id(clubId).stream()
                .map(team -> team.getId())
                .toList();
        if (teamIds.isEmpty()) {
            return false;
        }
        return hasTeamAccess(actor, teamIds);
    }

    public void requireClubAccess(User actor, UUID clubId) {
        if (!hasClubAccess(actor, clubId)) {
            throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied to club.");
        }
    }

    /**
     * Enforces membership only when at least one team id is present.
     * Unattached resources (no home/away team) are allowed through; the caller
     * is expected to already be role-gated (coach/admin).
     */
    public void requireTeamAccessIfPresent(User actor, Collection<UUID> teamIds) {
        List<UUID> cleanTeamIds = teamIds == null
                ? List.of()
                : teamIds.stream().filter(Objects::nonNull).toList();
        if (cleanTeamIds.isEmpty()) {
            return;
        }
        requireTeamAccess(actor, cleanTeamIds);
    }

    public boolean canAccessMatch(User actor, UUID matchId) {
        if (matchId == null || actor == null) return false;

        if (isAdmin(actor)) {
            return true;
        }

        Match match = matchRepository.findById(matchId).orElse(null);
        if (match == null) {
            return false;
        }
        if (match.getUploadedBy() != null && actor.getId() != null
                && actor.getId().equals(match.getUploadedBy().getId())) {
            return true;
        }

        List<UUID> matchTeamIds = Stream.of(match.getHomeTeam(), match.getAwayTeam())
                .filter(Objects::nonNull)
                .map(team -> team.getId())
                .filter(Objects::nonNull)
                .toList();
        return hasTeamAccess(actor, matchTeamIds);
    }

    public void requireMatchAccess(User actor, UUID matchId) {
        if (!canAccessMatch(actor, matchId)) {
            throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied to match.");
        }
    }

    /**
     * Write access: platform admin, the uploader, or a coach of the <em>home</em> team.
     * Away-team membership is view-only so an opponent coach cannot delete or edit the video.
     */
    public boolean canManageMatch(User actor, UUID matchId) {
        if (matchId == null || actor == null) return false;

        if (isAdmin(actor)) {
            return true;
        }

        Match match = matchRepository.findById(matchId).orElse(null);
        if (match == null) {
            return false;
        }
        if (match.getUploadedBy() != null && actor.getId() != null
                && actor.getId().equals(match.getUploadedBy().getId())) {
            return true;
        }
        if (!(actor instanceof Coach) || actor.getId() == null) {
            return false;
        }
        UUID homeTeamId = match.getHomeTeam() != null ? match.getHomeTeam().getId() : null;
        if (homeTeamId == null) {
            return false;
        }
        return coachRepository.existsByIdAndTeams_IdIn(actor.getId(), List.of(homeTeamId));
    }

    public void requireMatchManageAccess(User actor, UUID matchId) {
        if (!canManageMatch(actor, matchId)) {
            throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied to manage match.");
        }
    }

    public boolean canAccessClip(User actor, UUID clipId) {
        if (clipId == null || actor == null) return false;

        if (isAdmin(actor)) {
            return true;
        }

        UUID matchId = clipRepository.findMatchIdByClipId(clipId);
        return canAccessMatch(actor, matchId);
    }

    public void requireClipAccess(User actor, UUID clipId) {
        if (!canAccessClip(actor, clipId)) {
            throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied to clip.");
        }
    }

    public boolean canManageClip(User actor, UUID clipId) {
        if (clipId == null || actor == null) return false;
        if (isAdmin(actor)) {
            return true;
        }
        UUID matchId = clipRepository.findMatchIdByClipId(clipId);
        return canManageMatch(actor, matchId);
    }

    public void requireClipManageAccess(User actor, UUID clipId) {
        if (!canManageClip(actor, clipId)) {
            throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied to manage clip.");
        }
    }

    public boolean isAdmin(User actor) {
        if (actor == null) {
            return false;
        }
        if (actor.getRole() == UserRole.ADMIN || actor instanceof Admin) {
            return true;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return false;
        }
        if (!principalMatchesActor(actor, auth)) {
            return false;
        }
        return auth.getAuthorities().stream().anyMatch(granted -> {
            String authority = granted.getAuthority();
            return "ROLE_ADMIN".equals(authority) || "ROLE_admin".equals(authority);
        });
    }

    public Set<UUID> getAccessibleTeamIds(User actor) {
        if (actor == null || actor.getId() == null) {
            return Set.of();
        }

        if (isAdmin(actor)) {
            return new LinkedHashSet<>(teamRepository.findAllTeamIds());
        }

        UUID userId = actor.getId();

        if (actor instanceof Coach) {
            return new LinkedHashSet<>(coachRepository.findTeamIdsByCoachId(userId));
        }
        if (actor instanceof Player) {
            return new LinkedHashSet<>(playerRepository.findTeamIdsByPlayerId(userId));
        }
        if (actor instanceof Fan) {
            return new LinkedHashSet<>(fanRepository.findTeamIdsByFanId(userId));
        }

        return Set.of();
    }

    private boolean principalMatchesActor(User actor, Authentication auth) {
        if (actor.getId() == null) {
            return false;
        }
        String actorId = actor.getId().toString();
        if (auth.getPrincipal() instanceof Jwt jwt) {
            return actorId.equals(jwt.getSubject());
        }
        return actorId.equals(auth.getName());
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
