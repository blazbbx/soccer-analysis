package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.FieldConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.InviteKind;
import com.example.footballanalysis.model.db.ParentInvite;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.RegisterUserRequest;
import com.example.footballanalysis.model.responses.InviteTokenResponse;
import com.example.footballanalysis.model.responses.TeamInviteResponse;
import com.example.footballanalysis.model.responses.UserResponse;
import com.example.footballanalysis.repository.ParentInviteRepository;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParentInviteService {

    private static final int EXPIRATION_HOURS = 24;

    private final ParentInviteRepository parentInviteRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerParentRepository playerParentRepository;
    private final PlayerParentService playerParentService;
    private final UserRepository userRepository;
    private final KeycloakUserAdminService keycloakUserAdminService;
    private final AuditEventService auditEventService;
    private final UserAccessService userAccessService;
    private final ExtraHatService extraHatService;

    @Transactional
    public InviteTokenResponse create(UUID profileId, User actor) {
        PlayerProfile profile = requireParentOrAdmin(profileId, actor);
        ParentInvite invite = new ParentInvite();
        invite.setPlayerProfile(profile);
        invite.setCreatedByUserId(actor.getId());
        invite.setToken(UUID.randomUUID().toString());
        invite.setMaxUses(1);
        invite.setUsedCount(0);
        invite.setExpiresAt(LocalDateTime.now().plusHours(EXPIRATION_HOURS));
        parentInviteRepository.save(invite);
        log.info("Created parent invite {} for profile {}", invite.getId(), profileId);
        return new InviteTokenResponse(invite.getToken());
    }

    @Transactional(readOnly = true)
    public boolean hasToken(String token) {
        return token != null && parentInviteRepository.findByToken(token).isPresent();
    }

    @Transactional(readOnly = true)
    public TeamInviteResponse getInvite(String token) {
        return toResponse(findActive(token));
    }

    @Transactional
    public TeamInviteResponse accept(String token, User actor) {
        if (actor == null || actor.getId() == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required to accept an invite.");
        }
        extraHatService.requireKindAllowed(actor, InviteKind.PARENT);
        ParentInvite invite = findActiveForUpdate(token);
        if (invite.isExhausted()) {
            throw new ConflictException("error.team.invite.exhausted", new Object[]{invite.getId()}, "This invite link has reached its maximum number of uses.");
        }
        playerParentService.link(actor, invite.getPlayerProfile());
        extraHatService.ensureHat(actor, UserRole.PARENT);
        invite.setUsedCount(invite.getUsedCount() + 1);
        parentInviteRepository.save(invite);
        auditEventService.record(
                "PARENT_INVITE_ACCEPTED",
                actor.getId(),
                actor.getRole() != null ? actor.getRole().name() : null,
                "PROFILE",
                invite.getPlayerProfile().getId().toString(),
                "flow=direct_accept, inviteId=" + invite.getId());
        return toResponse(invite);
    }

    @Transactional
    public UserResponse register(RegisterUserRequest request) {
        ParentInvite invite = findActiveForUpdate(request.inviteToken());
        if (invite.isExhausted()) {
            throw new ConflictException("error.team.invite.exhausted", new Object[]{invite.getId()}, "This invite link has reached its maximum number of uses.");
        }
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new FieldConflictException("email",
                    "error.user.email.conflict", new Object[]{request.email()},
                    "Email already in use: " + request.email());
        }
        String keycloakUserId = keycloakUserAdminService.createUser(
                request.email().trim(),
                request.firstName().trim(),
                request.lastName().trim(),
                request.password(),
                UserRole.PARENT);
        try {
            Parent user = new Parent();
            user.setId(UUID.fromString(keycloakUserId));
            user.setRole(UserRole.PARENT);
            user.setEmail(request.email().trim());
            user.setFirstName(request.firstName().trim());
            user.setLastName(request.lastName().trim());
            User saved = userRepository.save(user);
            playerParentService.link(saved, invite.getPlayerProfile());
            invite.setUsedCount(invite.getUsedCount() + 1);
            parentInviteRepository.save(invite);
            auditEventService.record(
                    "PARENT_INVITE_ACCEPTED",
                    saved.getId(),
                    UserRole.PARENT.name(),
                    "PROFILE",
                    invite.getPlayerProfile().getId().toString(),
                    "flow=registration, inviteId=" + invite.getId());
            return new UserResponse(saved.getId(), saved.getEmail(), saved.getFirstName(), saved.getLastName(), saved.getRole(), saved.getCreatedAt(), null);
        } catch (RuntimeException ex) {
            try {
                keycloakUserAdminService.deleteUser(keycloakUserId);
            } catch (RuntimeException cleanupEx) {
                log.error("Failed to delete Keycloak user {}", keycloakUserId, cleanupEx);
            }
            throw ex;
        }
    }

    private PlayerProfile requireParentOrAdmin(UUID profileId, User actor) {
        if (actor == null || actor.getId() == null) {
            throw new UnauthorizedException("error.auth.unauthorized", new Object[0], "Authentication is required.");
        }
        PlayerProfile profile = playerProfileRepository.findById(profileId)
                .orElseThrow(() -> new NotFoundException(
                        "error.player_profile.not_found",
                        new Object[]{profileId},
                        "Player profile not found."));
        boolean admin = userAccessService.isAdmin(actor);
        boolean parentOfChild = playerParentRepository.existsById(new PlayerParentId(actor.getId(), profileId));
        boolean clubAdmin = userAccessService.isClubAdminOfProfile(actor, profileId);
        if (!admin && !parentOfChild && !clubAdmin) {
            throw new ForbiddenException(
                    "error.player_parent.not_linked",
                    new Object[0],
                    "Only a parent of this child or the club admin can invite another parent.");
        }
        return profile;
    }

    private ParentInvite findActive(String token) {
        ParentInvite invite = parentInviteRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException("error.team.invite.not_found", new Object[0], "Invite not found."));
        if (invite.isExpired()) {
            throw new ConflictException("error.team.invite.expired", new Object[]{invite.getId()}, "This invite link has expired.");
        }
        return invite;
    }

    private ParentInvite findActiveForUpdate(String token) {
        ParentInvite invite = parentInviteRepository.findByTokenForUpdate(token)
                .orElseThrow(() -> new NotFoundException("error.team.invite.not_found", new Object[0], "Invite not found."));
        if (invite.isExpired()) {
            throw new ConflictException("error.team.invite.expired", new Object[]{invite.getId()}, "This invite link has expired.");
        }
        return invite;
    }

    private TeamInviteResponse toResponse(ParentInvite invite) {
        int remaining = Math.max(invite.getMaxUses() - invite.getUsedCount(), 0);
        String childFirstName = invite.getPlayerProfile() != null ? invite.getPlayerProfile().getFirstName() : null;
        String childLastName = invite.getPlayerProfile() != null ? invite.getPlayerProfile().getLastName() : null;
        return new TeamInviteResponse(
                invite.getId(),
                null,
                null,
                UserRole.PARENT,
                null,
                inviteLink(invite.getToken()),
                invite.getMaxUses(),
                invite.getUsedCount(),
                remaining,
                invite.getExpiresAt(),
                invite.isExpired(),
                childFirstName,
                childLastName,
                InviteKind.PARENT);
    }

    private String inviteLink(String token) {
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/team-invites/")
                    .path(token)
                    .toUriString();
        } catch (IllegalStateException ex) {
            return "/api/team-invites/" + token;
        }
    }
}
