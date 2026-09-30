package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.ClubAdminInvite;
import com.example.footballanalysis.model.db.ClubAdminMembership;
import com.example.footballanalysis.model.db.user.ClubAdmin;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.repository.ClubAdminInviteRepository;
import com.example.footballanalysis.repository.ClubAdminMembershipRepository;
import com.example.footballanalysis.repository.ClubRepository;
import com.example.footballanalysis.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClubAdminInviteServiceTest {

    @Mock private ClubRepository clubRepository;
    @Mock private ClubAdminInviteRepository clubAdminInviteRepository;
    @Mock private ClubAdminMembershipRepository clubAdminMembershipRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserAccessService userAccessService;
    @Mock private KeycloakUserAdminService keycloakUserAdminService;
    @Mock private AuditEventService auditEventService;

    @Test
    void remove_rejectsLastAdmin() {
        UUID clubId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Club club = new Club();
        club.setId(clubId);
        ClubAdmin target = new ClubAdmin();
        target.setId(userId);
        target.setRole(UserRole.CLUB_ADMIN);
        ClubAdminMembership membership = new ClubAdminMembership();
        membership.setUser(target);
        membership.setClub(club);
        Jwt jwt = jwt();
        ClubAdmin actor = new ClubAdmin();
        actor.setId(UUID.randomUUID());
        actor.setRole(UserRole.CLUB_ADMIN);
        when(userAccessService.resolveCurrentUser(jwt)).thenReturn(actor);
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club));
        when(clubAdminMembershipRepository.findByClub_IdForUpdate(clubId)).thenReturn(List.of(membership));

        assertThatThrownBy(() -> service().remove(clubId, userId, jwt))
                .isInstanceOf(ConflictException.class);
        verify(clubAdminMembershipRepository, never()).delete(membership);
    }

    @Test
    void accept_rejectsOtherRole() {
        Coach coach = new Coach();
        coach.setId(UUID.randomUUID());
        coach.setRole(UserRole.COACH);

        assertThatThrownBy(() -> service().accept("token", coach))
                .isInstanceOf(BadRequestException.class);
        verify(keycloakUserAdminService, never()).createUser(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private ClubAdminInviteService service() {
        return new ClubAdminInviteService(
                clubRepository,
                clubAdminInviteRepository,
                clubAdminMembershipRepository,
                userRepository,
                userAccessService,
                keycloakUserAdminService,
                auditEventService);
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
