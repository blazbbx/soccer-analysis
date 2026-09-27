package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.PlayerParent;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.ChildResponse;
import com.example.footballanalysis.model.responses.ChildTeamResponse;
import com.example.footballanalysis.repository.ParentInviteRepository;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamInviteRepository;
import com.example.footballanalysis.repository.TeamRosterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerParentService {

    private final PlayerParentRepository playerParentRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final TeamRosterRepository teamRosterRepository;
    private final TeamInviteRepository teamInviteRepository;
    private final ParentInviteRepository parentInviteRepository;

    @Transactional
    public void link(User parent, UUID profileId) {
        if (parent == null || parent.getId() == null) {
            throw new NotFoundException("error.user.not_found", new Object[0], "Parent user is missing.");
        }
        PlayerProfile profile = playerProfileRepository.findById(profileId)
                .orElseThrow(() -> new NotFoundException(
                        "error.player_profile.not_found",
                        new Object[]{profileId},
                        "Player profile not found."));
        link(parent, profile);
    }

    @Transactional
    public void link(User parent, PlayerProfile profile) {
        if (parent == null || parent.getId() == null || profile == null || profile.getId() == null) {
            throw new NotFoundException("error.player_profile.not_found", new Object[0], "Player profile is missing.");
        }
        if (profile.getLinkedUser() != null && parent.getId().equals(profile.getLinkedUser().getId())) {
            throw new ConflictException(
                    "error.player_parent.own_profile",
                    new Object[0],
                    "A user cannot be the parent of their own player profile.");
        }
        PlayerParentId id = new PlayerParentId(parent.getId(), profile.getId());
        if (playerParentRepository.existsById(id)) {
            throw new ConflictException(
                    "error.player_parent.already_linked",
                    new Object[0],
                    "This parent is already linked to the player profile.");
        }
        PlayerParent link = new PlayerParent();
        link.setId(id);
        link.setUser(parent);
        link.setPlayerProfile(profile);
        playerParentRepository.save(link);
    }

    @Transactional
    public void attachChildLogin(User child, PlayerProfile profile) {
        if (child == null || child.getId() == null || profile == null || profile.getId() == null) {
            throw new NotFoundException("error.player_profile.not_found", new Object[0], "Player profile is missing.");
        }
        if (playerParentRepository.existsById(new PlayerParentId(child.getId(), profile.getId()))) {
            throw new ConflictException(
                    "error.player_parent.cannot_login_as_child",
                    new Object[0],
                    "A parent of this profile cannot use the child's login.");
        }
        if (profile.getLinkedUser() != null) {
            throw new ConflictException(
                    "error.player_profile.login_exists",
                    new Object[0],
                    "This player profile already has a login.");
        }
        profile.setLinkedUser(child);
        playerProfileRepository.save(profile);
    }

    @Transactional
    public void unlinkParent(UUID profileId, UUID parentId, User actor) {
        PlayerProfile profile = requireAdmin(profileId, actor);
        PlayerParentId linkId = new PlayerParentId(parentId, profileId);
        if (!playerParentRepository.existsById(linkId)) {
            throw new NotFoundException(
                    "error.player_parent.link_not_found",
                    new Object[]{parentId, profileId},
                    "This parent is not linked to the player profile.");
        }
        playerParentRepository.deleteById(linkId);
        playerParentRepository.flush();
        deleteIfUnowned(profile);
    }

    @Transactional
    public void detachChildLogin(UUID profileId, User actor) {
        PlayerProfile profile = requireAdmin(profileId, actor);
        if (profile.getLinkedUser() == null) {
            throw new ConflictException(
                    "error.player_profile.login_missing",
                    new Object[0],
                    "This player profile has no login.");
        }
        profile.setLinkedUser(null);
        playerProfileRepository.save(profile);
        playerProfileRepository.flush();
        deleteIfUnowned(profile);
    }

    private PlayerProfile requireAdmin(UUID profileId, User actor) {
        if (actor == null || actor.getId() == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required.");
        }
        PlayerProfile profile = playerProfileRepository.findById(profileId)
                .orElseThrow(() -> new NotFoundException(
                        "error.player_profile.not_found",
                        new Object[]{profileId},
                        "Player profile not found."));
        if (actor.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException(
                    "error.auth.forbidden",
                    new Object[0],
                    "Only an admin can change this profile link.");
        }
        return profile;
    }

    @Transactional
    public void detachUser(UUID userId) {
        if (userId == null) {
            return;
        }
        List<PlayerProfile> touched = new ArrayList<>();
        playerProfileRepository.findByLinkedUser_Id(userId).ifPresent(profile -> {
            profile.setLinkedUser(null);
            playerProfileRepository.save(profile);
            touched.add(profile);
        });
        for (PlayerParent link : playerParentRepository.findByUser_Id(userId)) {
            PlayerProfile profile = link.getPlayerProfile();
            playerParentRepository.delete(link);
            if (profile != null && profile.getId() != null && touched.stream().noneMatch(existing -> profile.getId().equals(existing.getId()))) {
                touched.add(profile);
            }
        }
        playerParentRepository.flush();
        for (PlayerProfile profile : touched) {
            deleteIfUnowned(profile);
        }
    }

    @Transactional
    public void deleteIfUnowned(PlayerProfile profile) {
        if (profile == null || profile.getId() == null || profile.getLinkedUser() != null) {
            return;
        }
        if (playerParentRepository.existsByPlayerProfile_Id(profile.getId())) {
            return;
        }
        teamRosterRepository.deleteByPlayerProfile_Id(profile.getId());
        teamInviteRepository.deleteByPlayerProfile_Id(profile.getId());
        parentInviteRepository.deleteByPlayerProfile_Id(profile.getId());
        playerProfileRepository.delete(profile);
        log.info("Deleted player profile {} with no parent and no login", profile.getId());
    }

    @Transactional(readOnly = true)
    public List<ChildResponse> listChildren(User parent) {
        if (parent == null || parent.getId() == null) {
            return List.of();
        }
        return playerParentRepository.findByUser_Id(parent.getId()).stream()
                .map(PlayerParent::getPlayerProfile)
                .map(this::toChild)
                .toList();
    }

    private ChildResponse toChild(PlayerProfile profile) {
        List<ChildTeamResponse> teams = profile.getId() == null
                ? List.of()
                : playerProfileRepository.findTeamsByProfileId(profile.getId()).stream()
                        .map(team -> {
                            var club = team.getClub();
                            return new ChildTeamResponse(
                                    team.getId(),
                                    team.getName(),
                                    club != null ? club.getId() : null,
                                    club != null ? club.getName() : null);
                        })
                        .toList();
        return new ChildResponse(
                profile.getId(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getLinkedUser() != null ? profile.getLinkedUser().getId() : null,
                teams);
    }
}
