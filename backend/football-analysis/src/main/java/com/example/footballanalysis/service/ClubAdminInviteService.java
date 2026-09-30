package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.FieldConflictException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.exception.UnauthorizedException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.ClubAdminInvite;
import com.example.footballanalysis.model.db.ClubAdminMembership;
import com.example.footballanalysis.model.db.ClubAdminMembershipId;
import com.example.footballanalysis.model.db.InviteKind;
import com.example.footballanalysis.model.db.user.ClubAdmin;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.RegisterUserRequest;
import com.example.footballanalysis.model.responses.ClubAdminResponse;
import com.example.footballanalysis.model.responses.InviteTokenResponse;
import com.example.footballanalysis.model.responses.TeamInviteResponse;
import com.example.footballanalysis.model.responses.UserResponse;
import com.example.footballanalysis.repository.ClubAdminInviteRepository;
import com.example.footballanalysis.repository.ClubAdminMembershipRepository;
import com.example.footballanalysis.repository.ClubRepository;
import com.example.footballanalysis.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClubAdminInviteService {

    private static final int EXPIRATION_HOURS = 24;

    private final ClubRepository clubRepository;
    private final ClubAdminInviteRepository clubAdminInviteRepository;
    private final ClubAdminMembershipRepository clubAdminMembershipRepository;
    private final UserRepository userRepository;
    private final UserAccessService userAccessService;
    private final KeycloakUserAdminService keycloakUserAdminService;
    private final AuditEventService auditEventService;

    @Transactional
    public InviteTokenResponse create(UUID clubId, Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        Club club = requireClub(clubId);
        userAccessService.requireClubAdmin(actor, clubId);
        ClubAdminInvite invite = new ClubAdminInvite();
        invite.setClub(club);
        invite.setCreatedByUserId(actor.getId());
        invite.setToken(UUID.randomUUID().toString());
        invite.setMaxUses(1);
        invite.setUsedCount(0);
        invite.setExpiresAt(LocalDateTime.now().plusHours(EXPIRATION_HOURS));
        clubAdminInviteRepository.save(invite);
        log.atInfo()
                .setMessage("Created club admin invite {}")
                .addArgument(invite.getId())
                .addKeyValue("clubId", clubId)
                .log();
        return new InviteTokenResponse(invite.getToken());
    }

    @Transactional(readOnly = true)
    public List<ClubAdminResponse> list(UUID clubId, Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        requireClub(clubId);
        userAccessService.requireClubAdmin(actor, clubId);
        return clubAdminMembershipRepository.findByClub_Id(clubId).stream()
                .sorted(Comparator.comparing(ClubAdminMembership::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toAdminResponse)
                .toList();
    }

    @Transactional
    public void remove(UUID clubId, UUID userId, Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        requireClub(clubId);
        userAccessService.requireClubAdmin(actor, clubId);
        List<ClubAdminMembership> locked = clubAdminMembershipRepository.findByClub_IdForUpdate(clubId);
        ClubAdminMembership target = locked.stream()
                .filter(membership -> membership.getUser() != null && userId.equals(membership.getUser().getId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "error.club_admin.not_found",
                        new Object[]{userId},
                        "Club admin not found: " + userId));
        if (locked.size() <= 1) {
            throw new ConflictException(
                    "error.club_admin.last",
                    new Object[0],
                    "The last club admin cannot be removed.");
        }
        clubAdminMembershipRepository.delete(target);
        auditEventService.record(
                "CLUB_ADMIN_REMOVED",
                actor.getId(),
                actor.getRole() != null ? actor.getRole().name() : null,
                "CLUB",
                clubId.toString(),
                "userId=" + userId);
        log.atInfo()
                .setMessage("Removed club admin {}")
                .addArgument(userId)
                .addKeyValue("clubId", clubId)
                .addKeyValue("targetUserId", userId)
                .log();
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
        if (actor.getRole() != UserRole.CLUB_ADMIN) {
            throw new BadRequestException(
                    "error.club_admin.role_not_allowed",
                    new Object[]{actor.getRole()},
                    "Only a club admin account can accept this invite.");
        }
        ClubAdminInvite invite = findActiveForUpdate(token);
        if (invite.isExhausted()) {
            throw new ConflictException("error.team.invite.exhausted", new Object[]{invite.getId()}, "This invite link has reached its maximum number of uses.");
        }
        link(actor, invite.getClub());
        invite.setUsedCount(invite.getUsedCount() + 1);
        clubAdminInviteRepository.save(invite);
        auditAdded(actor.getId(), UserRole.CLUB_ADMIN.name(), invite.getClub().getId(), actor.getId());
        return toResponse(invite);
    }

    @Transactional
    public UserResponse register(RegisterUserRequest request) {
        ClubAdminInvite invite = findActiveForUpdate(request.inviteToken());
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
                UserRole.CLUB_ADMIN);
        try {
            ClubAdmin user = new ClubAdmin();
            user.setId(UUID.fromString(keycloakUserId));
            user.setRole(UserRole.CLUB_ADMIN);
            user.setEmail(request.email().trim());
            user.setFirstName(request.firstName().trim());
            user.setLastName(request.lastName().trim());
            User saved = userRepository.save(user);
            link(saved, invite.getClub());
            invite.setUsedCount(invite.getUsedCount() + 1);
            clubAdminInviteRepository.save(invite);
            auditAdded(saved.getId(), UserRole.CLUB_ADMIN.name(), invite.getClub().getId(), saved.getId());
            return new UserResponse(
                    saved.getId(),
                    saved.getEmail(),
                    saved.getFirstName(),
                    saved.getLastName(),
                    saved.getRole(),
                    saved.getCreatedAt(),
                    null);
        } catch (RuntimeException ex) {
            try {
                keycloakUserAdminService.deleteUser(keycloakUserId);
            } catch (RuntimeException cleanupEx) {
                log.error("Failed to delete Keycloak user {}", keycloakUserId, cleanupEx);
            }
            throw ex;
        }
    }

    private void link(User user, Club club) {
        if (clubAdminMembershipRepository.existsByUser_IdAndClub_Id(user.getId(), club.getId())) {
            throw new ConflictException(
                    "error.club_admin.already_member",
                    new Object[]{user.getId(), club.getId()},
                    "This club admin is already a member of the club.");
        }
        ClubAdminMembership membership = new ClubAdminMembership();
        membership.setId(new ClubAdminMembershipId(user.getId(), club.getId()));
        membership.setUser(user);
        membership.setClub(club);
        clubAdminMembershipRepository.save(membership);
    }

    private void auditAdded(UUID actorId, String actorRole, UUID clubId, UUID targetUserId) {
        auditEventService.record(
                "CLUB_ADMIN_ADDED",
                actorId,
                actorRole,
                "CLUB",
                clubId.toString(),
                "userId=" + targetUserId + ",source=invite");
        log.atInfo()
                .setMessage("Added club admin {}")
                .addArgument(targetUserId)
                .addKeyValue("clubId", clubId)
                .addKeyValue("targetUserId", targetUserId)
                .log();
    }

    private Club requireClub(UUID clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException(
                        "error.club.not_found", new Object[]{clubId}, "Club not found: " + clubId));
    }

    private ClubAdminInvite findActive(String token) {
        ClubAdminInvite invite = clubAdminInviteRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException("error.team.invite.not_found", new Object[0], "Invite not found."));
        if (invite.isExpired()) {
            throw new ConflictException("error.team.invite.expired", new Object[]{invite.getId()}, "This invite link has expired.");
        }
        return invite;
    }

    private ClubAdminInvite findActiveForUpdate(String token) {
        ClubAdminInvite invite = clubAdminInviteRepository.findByTokenForUpdate(token)
                .orElseThrow(() -> new NotFoundException("error.team.invite.not_found", new Object[0], "Invite not found."));
        if (invite.isExpired()) {
            throw new ConflictException("error.team.invite.expired", new Object[]{invite.getId()}, "This invite link has expired.");
        }
        return invite;
    }

    private ClubAdminResponse toAdminResponse(ClubAdminMembership membership) {
        User user = membership.getUser();
        return new ClubAdminResponse(
                user != null ? user.getId() : null,
                user != null ? user.getFirstName() : null,
                user != null ? user.getLastName() : null,
                user != null ? user.getEmail() : null,
                membership.getCreatedAt());
    }

    private TeamInviteResponse toResponse(ClubAdminInvite invite) {
        int remaining = Math.max(invite.getMaxUses() - invite.getUsedCount(), 0);
        return new TeamInviteResponse(
                invite.getId(),
                null,
                null,
                UserRole.CLUB_ADMIN,
                null,
                inviteLink(invite.getToken()),
                invite.getMaxUses(),
                invite.getUsedCount(),
                remaining,
                invite.getExpiresAt(),
                invite.isExpired(),
                null,
                null,
                InviteKind.CLUB_ADMIN);
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
