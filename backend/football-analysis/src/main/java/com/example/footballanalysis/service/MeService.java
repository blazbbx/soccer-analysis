package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.ClubAdminMembership;
import com.example.footballanalysis.model.db.PlayerParent;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.MeViewsResponse;
import com.example.footballanalysis.repository.ClubAdminMembershipRepository;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeService {

    static final List<UserRole> HAT_RANK = List.of(
            UserRole.ADMIN,
            UserRole.CLUB_ADMIN,
            UserRole.COACH,
            UserRole.PLAYER,
            UserRole.PARENT,
            UserRole.FAN);

    private final UserAccessService userAccessService;
    private final UserRoleSyncService userRoleSyncService;
    private final ClubAdminMembershipRepository clubAdminMembershipRepository;
    private final TeamMembershipQuery teamMembershipQuery;
    private final PlayerParentRepository playerParentRepository;
    private final PlayerProfileRepository playerProfileRepository;

    public MeViewsResponse getViews(Jwt jwt) {
        User actor = userAccessService.resolveCurrentUser(jwt);
        Set<UserRole> jwtHats = userRoleSyncService.extractAllClientRoles(jwt);
        List<UserRole> hats = HAT_RANK.stream().filter(jwtHats::contains).toList();
        UUID userId = actor.getId();

        return new MeViewsResponse(
                actor.getRole(),
                hats,
                userAccessService.hasHat(actor, UserRole.CLUB_ADMIN)
                        ? clubAdminMembershipRepository.findByUser_Id(userId).stream()
                        .map(ClubAdminMembership::getId)
                        .map(id -> id.getClubId())
                        .toList()
                        : List.of(),
                userAccessService.hasHat(actor, UserRole.COACH)
                        ? teamMembershipQuery.findCoachTeamIds(userId)
                        : List.of(),
                userAccessService.hasHat(actor, UserRole.PARENT)
                        ? playerParentRepository.findByUser_Id(userId).stream()
                        .map(PlayerParent::getId)
                        .map(id -> id.getPlayerProfileId())
                        .toList()
                        : List.of(),
                userAccessService.hasHat(actor, UserRole.PLAYER)
                        ? playerProfileRepository.findTeamIdsByLinkedUserId(userId)
                        : List.of(),
                userAccessService.hasHat(actor, UserRole.FAN)
                        ? teamMembershipQuery.findFanTeamIds(userId)
                        : List.of());
    }
}
