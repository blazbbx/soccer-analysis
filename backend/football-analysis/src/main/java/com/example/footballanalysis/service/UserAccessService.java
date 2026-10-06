package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.Team;
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
    private final PlayerProfileRepository playerProfileRepository;
    private final MatchRepository matchRepository;
    private final ClipRepository clipRepository;
    private final TeamRepository teamRepository;
    private final ClubRepository clubRepository;
    private final PlayerParentRepository playerParentRepository;
    private final ClubAdminMembershipRepository clubAdminMembershipRepository;
    private final TeamMembershipQuery teamMembershipQuery;

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

        if (hasHat(actor, UserRole.COACH) && teamMembershipQuery.isCoachOnAnyTeam(userId, cleanTeamIds)) {
            return true;
        }
        if (hasHat(actor, UserRole.PLAYER)
                && playerProfileRepository.existsByLinkedUser_IdAndTeam_IdIn(userId, cleanTeamIds)) {
            return true;
        }
        if (hasHat(actor, UserRole.FAN) && teamMembershipQuery.isFanOnAnyTeam(userId, cleanTeamIds)) {
            return true;
        }
        return hasHat(actor, UserRole.PARENT)
                && playerParentRepository.existsByUser_IdAndPlayerProfile_Team_IdIn(userId, cleanTeamIds);
    }

    public void requireTeamAccess(User actor, Collection<UUID> teamIds) {
        if (!hasTeamAccess(actor, teamIds)) {
            throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied to team.");
        }
    }

    /**
     * Team chat for members, platform admins, and a club admin of that team's club.
     * Another club stays closed. This does not grant match or clip access.
     */
    public boolean canAccessTeamChat(User actor, UUID teamId) {
        if (teamId == null) {
            return false;
        }
        if (hasTeamAccess(actor, List.of(teamId))) {
            return true;
        }
        Team team = teamRepository.findById(teamId).orElse(null);
        UUID clubId = team != null && team.getClub() != null ? team.getClub().getId() : null;
        return isClubAdmin(actor, clubId);
    }

    public void requireTeamChatAccess(User actor, UUID teamId) {
        if (!canAccessTeamChat(actor, teamId)) {
            throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied to team chat.");
        }
    }

    public boolean hasClubAccess(User actor, UUID clubId) {
        if (clubId == null || actor == null) return false;
        if (isAdmin(actor)) return true;
        var club = clubRepository.findById(clubId).orElse(null);
        if (club == null) return false;
        if (isClubAdmin(actor, clubId)) return true;
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
     * A club admin may manage parent links of a player profile only when that
     * profile is on a team of a club they administer.
     */
    public boolean isClubAdminOfProfile(User actor, UUID profileId) {
        if (actor == null || profileId == null || !hasHat(actor, UserRole.CLUB_ADMIN)) {
            return false;
        }
        return playerProfileRepository.findTeamsByProfileId(profileId).stream()
                .anyMatch(team -> team.getClub() != null && isClubAdmin(actor, team.getClub().getId()));
    }

    public boolean isClubAdmin(User actor, UUID clubId) {
        if (actor == null || actor.getId() == null || clubId == null) {
            return false;
        }
        if (!hasHat(actor, UserRole.CLUB_ADMIN)) {
            return false;
        }
        return clubAdminMembershipRepository.existsByUser_IdAndClub_Id(actor.getId(), clubId);
    }

    public void requireClubAdmin(User actor, UUID clubId) {
        if (isAdmin(actor) || isClubAdmin(actor, clubId)) {
            return;
        }
        throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied to manage club.");
    }

    /**
     * Team rename, delete, and roster writes. A coach still needs membership on the team.
     * A club admin may do the same only for a team whose club they administer.
     * This does not open matches or clips. Team chat uses {@link #canAccessTeamChat}.
     */
    public void requireClubTeamManage(User actor, UUID teamId) {
        if (isAdmin(actor)) {
            return;
        }
        Team team = teamId == null ? null : teamRepository.findById(teamId).orElse(null);
        UUID clubId = team != null && team.getClub() != null ? team.getClub().getId() : null;
        if (isClubAdmin(actor, clubId)) {
            return;
        }
        if (actor != null && hasHat(actor, UserRole.COACH)
                && teamMembershipQuery.isCoachOnTeam(actor.getId(), teamId)) {
            return;
        }
        throw new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied to manage team.");
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
        if (isClubAdminOfMatch(actor, match)) {
            return true;
        }

        List<UUID> matchTeamIds = Stream.of(match.getHomeTeam(), match.getAwayTeam())
                .filter(Objects::nonNull)
                .map(team -> team.getId())
                .filter(Objects::nonNull)
                .toList();
        return hasTeamAccess(actor, matchTeamIds);
    }

    private boolean isClubAdminOfMatch(User actor, Match match) {
        return Stream.of(match.getHomeTeam(), match.getAwayTeam())
                .filter(Objects::nonNull)
                .map(Team::getClub)
                .filter(Objects::nonNull)
                .map(club -> club.getId())
                .filter(Objects::nonNull)
                .anyMatch(clubId -> isClubAdmin(actor, clubId));
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
        if (!hasHat(actor, UserRole.COACH) || actor.getId() == null) {
            return false;
        }
        UUID homeTeamId = match.getHomeTeam() != null ? match.getHomeTeam().getId() : null;
        if (homeTeamId == null) {
            return false;
        }
        return teamMembershipQuery.isCoachOnTeam(actor.getId(), homeTeamId);
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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return actor.getRole() == UserRole.ADMIN || actor instanceof Admin;
        }
        if (!auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
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
        LinkedHashSet<UUID> teamIds = new LinkedHashSet<>();
        if (hasHat(actor, UserRole.COACH)) {
            teamIds.addAll(teamMembershipQuery.findCoachTeamIds(userId));
        }
        if (hasHat(actor, UserRole.PLAYER)) {
            teamIds.addAll(playerProfileRepository.findTeamIdsByLinkedUserId(userId));
        }
        if (hasHat(actor, UserRole.FAN)) {
            teamIds.addAll(teamMembershipQuery.findFanTeamIds(userId));
        }
        if (hasHat(actor, UserRole.PARENT)) {
            teamIds.addAll(playerParentRepository.findTeamIdsByUserId(userId));
        }
        return teamIds;
    }

    public Set<UUID> getAccessibleTeamIds(User actor, UserRole role) {
        if (role == null || actor == null || actor.getId() == null || !hasHat(actor, role)) return Set.of();
        if (role == UserRole.ADMIN) return new LinkedHashSet<>(teamRepository.findAllTeamIds());
        UUID userId = actor.getId();
        return switch (role) {
            case COACH -> new LinkedHashSet<>(teamMembershipQuery.findCoachTeamIds(userId));
            case PLAYER -> new LinkedHashSet<>(playerProfileRepository.findTeamIdsByLinkedUserId(userId));
            case FAN -> new LinkedHashSet<>(teamMembershipQuery.findFanTeamIds(userId));
            case PARENT -> new LinkedHashSet<>(playerParentRepository.findTeamIdsByUserId(userId));
            case CLUB_ADMIN -> clubAdminMembershipRepository.findByUser_Id(userId).stream()
                    .flatMap(membership -> teamRepository.findByClub_Id(membership.getClub().getId()).stream())
                    .map(Team::getId).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            default -> Set.of();
        };
    }

    /**
     * Live Keycloak hat on this actor. Tests without Authentication fall back to {@code users.role}.
     */
    public boolean hasHat(User actor, UserRole role) {
        if (actor == null || role == null) {
            return false;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return actor.getRole() == role;
        }
        if (!principalMatchesActor(actor, auth)) {
            return false;
        }
        String expected = role.name();
        return auth.getAuthorities().stream().anyMatch(granted -> {
            String authority = granted.getAuthority();
            if (authority == null) {
                return false;
            }
            String name = authority.startsWith("ROLE_") ? authority.substring(5) : authority;
            return expected.equalsIgnoreCase(name);
        });
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
