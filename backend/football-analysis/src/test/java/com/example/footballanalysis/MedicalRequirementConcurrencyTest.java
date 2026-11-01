package com.example.footballanalysis;

import com.example.footballanalysis.config.ClockConfig;
import com.example.footballanalysis.service.MedicalRequirementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two club admins switch off the two clubs of the same child at the same moment, on a real PostgreSQL.
 * Each transaction flips its own club and then checks the child. The second one to get the profile lock
 * must see the first one's committed switch, so the child ends up not required and the grace clock runs.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({MedicalRequirementService.class, ClockConfig.class})
class MedicalRequirementConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MedicalRequirementService requirement;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void twoSimultaneousSwitchOffsLeaveTheChildNotRequired() throws Exception {
        UUID profileId = UUID.randomUUID();
        jdbc.update("insert into player_profiles (id, first_name, last_name, created_at) values (?, 'A', 'B', now())", profileId);
        // The grace clock only starts when there is a current file to remove.
        jdbc.update("""
                insert into medical_certificates (id, player_profile_id, valid_until, object_key, content_type, size_bytes,
                    submitted_by_role, acknowledged_at, acknowledgement_version, created_at, completed_at, evidence)
                values (?, ?, date '2027-05-03', 'medical/key', 'application/pdf', 10, 'PARENT', now(), 'sport-medical-v1', now(), now(), 'FILE')
                """, UUID.randomUUID(), profileId);
        UUID first = clubWithTeamOf(profileId);
        UUID second = clubWithTeamOf(profileId);

        CountDownLatch bothFlipped = new CountDownLatch(2);
        CompletableFuture<Set<UUID>> one = CompletableFuture.supplyAsync(() -> switchOff(first, profileId, bothFlipped));
        CompletableFuture<Set<UUID>> two = CompletableFuture.supplyAsync(() -> switchOff(second, profileId, bothFlipped));
        List<Set<UUID>> after = List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));

        // The first to lock still sees the other club ON (not yet committed); the second sees both OFF.
        assertThat(after).containsExactlyInAnyOrder(Set.of(profileId), Set.of());
        Timestamp since = jdbc.queryForObject(
                "select medical_not_required_since from player_profiles where id = ?", Timestamp.class, profileId);
        assertThat(since).isNotNull();
    }

    @Test
    void aChildNoLongerRequiredKeepsTheClubsThatRequiredItUntilRequiredAgain() {
        UUID profileId = UUID.randomUUID();
        jdbc.update("insert into player_profiles (id, first_name, last_name, created_at) values (?, 'A', 'B', now())", profileId);
        UUID clubId = clubWithTeamOf(profileId);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            Map<UUID, Set<UUID>> before = requirement.requiringClubIdsNow(List.of(profileId));
            jdbc.update("update clubs set medical_mode = 'OFF' where id = ?", clubId);
            requirement.recalculate(List.of(profileId), before);
        });
        assertThat(lastRequiredClubs(profileId)).containsExactly(clubId);

        jdbc.update("update clubs set medical_mode = 'ON' where id = ?", clubId);
        tx.executeWithoutResult(status -> requirement.recalculate(List.of(profileId)));
        assertThat(lastRequiredClubs(profileId)).isEmpty();
    }

    private UUID[] lastRequiredClubs(UUID profileId) {
        return jdbc.queryForObject("select medical_last_required_club_ids from player_profiles where id = ?",
                (rs, row) -> (UUID[]) rs.getArray(1).getArray(), profileId);
    }

    /** The settings flow: flip the club, wait until the other admin flipped too, then lock and check. */
    private Set<UUID> switchOff(UUID clubId, UUID profileId, CountDownLatch bothFlipped) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            // Loads Team and Club entities while both clubs are still ON, as requiredBefore does.
            assertThat(requirement.requiredProfileIds(List.of(profileId))).containsExactly(profileId);
            jdbc.update("update clubs set medical_mode = 'OFF' where id = ?", clubId);
            bothFlipped.countDown();
            await(bothFlipped);
            requirement.lockProfiles(List.of(profileId));
            Set<UUID> requiredAfter = Set.copyOf(requirement.requiredProfileIdsNow(List.of(profileId)));
            requirement.recalculate(List.of(profileId));
            return requiredAfter;
        });
    }

    private UUID clubWithTeamOf(UUID profileId) {
        UUID clubId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        jdbc.update("insert into clubs (id, name, created_at, medical_mode) values (?, ?, now(), 'ON')", clubId, "Club " + clubId);
        jdbc.update("insert into teams (id, name, club_id) values (?, ?, ?)", teamId, "Team " + teamId, clubId);
        jdbc.update("insert into team_roster (player_profile_id, team_id) values (?, ?)", profileId, teamId);
        return clubId;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
