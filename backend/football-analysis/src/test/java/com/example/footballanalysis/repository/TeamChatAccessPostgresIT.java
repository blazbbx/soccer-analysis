package com.example.footballanalysis.repository;

import com.example.footballanalysis.service.TestHats;

import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.ClubAdminMembership;
import com.example.footballanalysis.model.db.ClubAdminMembershipId;
import com.example.footballanalysis.model.db.PlayerParent;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.TeamRoster;
import com.example.footballanalysis.model.db.TeamRosterId;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The native chat access query of TeamRepository on a real PostgreSQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class TeamChatAccessPostgresIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private EntityManager em;
    @Autowired private TeamRepository teamRepository;

    @Test
    void findsEveryKindOfMemberThroughTheRosterButNotOtherTeams() {
        Club club = new Club();
        club.setName("Club " + UUID.randomUUID());
        em.persist(club);

        Team team = team("Team", club);
        Team otherTeam = team("Other", null);

        User coach = user(TestHats.user(UserRole.COACH), UserRole.COACH);
        User fan = user(TestHats.user(UserRole.FAN), UserRole.FAN);
        User player = user(TestHats.user(UserRole.PLAYER), UserRole.PLAYER);
        User parent = user(TestHats.user(UserRole.PARENT), UserRole.PARENT);
        User clubAdmin = user(TestHats.user(UserRole.PARENT), UserRole.CLUB_ADMIN);
        User otherPlayer = user(TestHats.user(UserRole.PLAYER), UserRole.PLAYER);
        User otherParent = user(TestHats.user(UserRole.PARENT), UserRole.PARENT);
        User outsider = user(TestHats.user(UserRole.PARENT), UserRole.PARENT);

        PlayerProfile profile = profile(player, team);
        parentOf(parent, profile);
        PlayerProfile otherProfile = profile(otherPlayer, otherTeam);
        parentOf(otherParent, otherProfile);

        ClubAdminMembership membership = new ClubAdminMembership();
        membership.setId(new ClubAdminMembershipId(clubAdmin.getId(), club.getId()));
        membership.setUser(clubAdmin);
        membership.setClub(club);
        em.persist(membership);
        em.flush();
        em.createNativeQuery("INSERT INTO coach_teams (coach_id, team_id) VALUES (?1, ?2)")
                .setParameter(1, coach.getId()).setParameter(2, team.getId()).executeUpdate();
        em.createNativeQuery("INSERT INTO fan_favorite_teams (fan_id, team_id) VALUES (?1, ?2)")
                .setParameter(1, fan.getId()).setParameter(2, team.getId()).executeUpdate();

        List<UUID> asked = List.of(coach.getId(), fan.getId(), player.getId(), parent.getId(), clubAdmin.getId(),
                otherPlayer.getId(), otherParent.getId(), outsider.getId());

        assertThat(Set.copyOf(teamRepository.findUserIdsWithChatAccess(team.getId(), asked)))
                .containsExactlyInAnyOrder(coach.getId(), fan.getId(), player.getId(), parent.getId(), clubAdmin.getId());
        assertThat(teamRepository.findUserIdsWithChatAccess(otherTeam.getId(), asked))
                .containsExactlyInAnyOrder(otherPlayer.getId(), otherParent.getId());

        // The chat role label queries (1.8).
        assertThat(teamRepository.findClubAdminIdsOfTeam(team.getId(), asked)).containsExactly(clubAdmin.getId());
        assertThat(teamRepository.findClubAdminIdsOfTeam(otherTeam.getId(), asked)).isEmpty();
        assertThat(teamRepository.findCoachIdsOfTeam(team.getId(), asked)).containsExactly(coach.getId());
        assertThat(teamRepository.findCoachIdsOfTeam(otherTeam.getId(), asked)).isEmpty();
    }

    private Team team(String name, Club club) {
        Team team = new Team();
        team.setName(name + " " + UUID.randomUUID());
        team.setClub(club);
        em.persist(team);
        return team;
    }

    private <T extends User> T user(T user, UserRole role) {
        user.setId(UUID.randomUUID());
        user.setEmail(role.name().toLowerCase() + "-" + user.getId() + "@test.test");
        user.setFirstName(role.name());
        user.setLastName("Test");
        TestHats.grant(user, role);
        em.persist(user);
        return user;
    }

    private PlayerProfile profile(User linkedUser, Team team) {
        PlayerProfile profile = new PlayerProfile();
        profile.setFirstName("Kid");
        profile.setLastName("Test");
        profile.setLinkedUser(linkedUser);
        em.persist(profile);
        TeamRoster row = new TeamRoster();
        row.setId(new TeamRosterId(profile.getId(), team.getId()));
        row.setPlayerProfile(profile);
        row.setTeam(team);
        em.persist(row);
        return profile;
    }

    private void parentOf(User parent, PlayerProfile profile) {
        PlayerParent link = new PlayerParent();
        link.setId(new PlayerParentId(parent.getId(), profile.getId()));
        link.setUser(parent);
        link.setPlayerProfile(profile);
        em.persist(link);
    }
}
