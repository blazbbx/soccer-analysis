package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.CreateCupRequest;
import com.example.footballanalysis.model.requests.CupMatchScheduleRequest;
import com.example.footballanalysis.model.requests.CupTeamCreateRequest;
import com.example.footballanalysis.model.requests.UpdateCupRequest;
import com.example.footballanalysis.model.requests.UpdateMatchScoreRequest;
import com.example.footballanalysis.model.responses.CupListItem;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import com.example.footballanalysis.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The cup fixes on a real PostgreSQL with the V15 migration: nullable creator, unique indexes, the leg column,
 * and deleting a cup with its teams and matches. Each service call commits on its own.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import({CupService.class, CupAccessPolicy.class, CupStandingsService.class, UserAccessService.class, TestHats.class,
        TeamMembershipQuery.class, AuditEventService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CupPostgresTest {

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

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 3, 10, 0);

    @Autowired private CupService cupService;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void deletingTheCreatorKeepsTheCupForTheAdmin() {
        User coach = insertUser("COACH");
        User admin = insertUser("ADMIN");
        UUID cupId = cupService.createCup(new CreateCupRequest("Őszi Kupa"), coach).id();

        jdbc.update("delete from users where id = ?", coach.getId());

        assertThat(jdbc.queryForObject("select created_by_id from cups where id = ?", UUID.class, cupId)).isNull();
        CupListItem row = cupService.listAllCups(admin, UserRole.ADMIN, null, 1, 50).items().stream()
                .filter(item -> item.id().equals(cupId)).findFirst().orElseThrow();
        assertThat(row.createdById()).isNull();
        assertThat(row.canManage()).isTrue();
        assertThat(cupService.renameCup(cupId, new UpdateCupRequest("Téli Kupa"), admin).name()).isEqualTo("Téli Kupa");
        User otherCoach = insertUser("COACH");
        assertThatThrownBy(() -> cupService.renameCup(cupId, new UpdateCupRequest("X"), otherCoach))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void returnMatchGetsLegTwoAndAThirdMatchIsRefused() {
        User coach = insertUser("COACH");
        UUID cupId = cupService.createCup(new CreateCupRequest("Kupa"), coach).id();
        UUID a = cupService.addTeamToCup(cupId, new CupTeamCreateRequest("A", null), coach).id();
        UUID b = cupService.addTeamToCup(cupId, new CupTeamCreateRequest("B", null), coach).id();

        assertThat(cupService.scheduleMatch(cupId, new CupMatchScheduleRequest(a, b, TIME), coach).leg()).isEqualTo(1);
        assertThat(cupService.scheduleMatch(cupId, new CupMatchScheduleRequest(b, a, TIME.plusDays(7)), coach).leg()).isEqualTo(2);
        assertThatThrownBy(() -> cupService.scheduleMatch(cupId, new CupMatchScheduleRequest(a, b, TIME.plusDays(14)), coach))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void theIndexesStopDuplicatesThatSlipPastTheService() {
        User coach = insertUser("COACH");
        UUID cupId = cupService.createCup(new CreateCupRequest("Kupa"), coach).id();
        UUID a = cupService.addTeamToCup(cupId, new CupTeamCreateRequest("FTC U11", null), coach).id();
        UUID b = cupService.addTeamToCup(cupId, new CupTeamCreateRequest("MTK U11", null), coach).id();
        cupService.scheduleMatch(cupId, new CupMatchScheduleRequest(a, b, TIME), coach);

        assertThatThrownBy(() -> jdbc.update("insert into cup_teams (id, created_at, name, cup_id) values (?, now(), ' ftc u11', ?)",
                UUID.randomUUID(), cupId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertMatch(cupId, b, a, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertMatch(cupId, b, a, 3)).isInstanceOf(DataIntegrityViolationException.class);
        insertMatch(cupId, b, a, 2);
    }

    @Test
    void sameNameIsAConflict() {
        User coach = insertUser("COACH");
        UUID cupId = cupService.createCup(new CreateCupRequest("Kupa"), coach).id();
        cupService.addTeamToCup(cupId, new CupTeamCreateRequest("FTC U11", null), coach);

        assertThatThrownBy(() -> cupService.addTeamToCup(cupId, new CupTeamCreateRequest("ftc u11 ", null), coach))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deletingACupRemovesItsTeamsAndMatches() {
        User coach = insertUser("COACH");
        User admin = insertUser("ADMIN");
        UUID cupId = cupService.createCup(new CreateCupRequest("Kupa"), coach).id();
        UUID a = cupService.addTeamToCup(cupId, new CupTeamCreateRequest("A", null), coach).id();
        UUID b = cupService.addTeamToCup(cupId, new CupTeamCreateRequest("B", null), coach).id();
        UUID matchId = cupService.scheduleMatch(cupId, new CupMatchScheduleRequest(a, b, TIME), coach).id();
        cupService.recordScore(cupId, matchId, new UpdateMatchScoreRequest(2, 1), coach);

        assertThatThrownBy(() -> cupService.deleteCup(cupId, coach)).isInstanceOf(ConflictException.class);

        cupService.deleteCup(cupId, admin);

        assertThat(jdbc.queryForObject("select count(*) from cups where id = ?", Integer.class, cupId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from cup_teams where cup_id = ?", Integer.class, cupId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from cup_matches where cup_id = ?", Integer.class, cupId)).isZero();
    }

    private void insertMatch(UUID cupId, UUID home, UUID away, int leg) {
        jdbc.update("""
                insert into cup_matches (id, created_at, played, scheduled_at, away_team_id, cup_id, home_team_id, leg)
                values (?, now(), false, now(), ?, ?, ?, ?)""", UUID.randomUUID(), away, cupId, home, leg);
    }

    private User insertUser(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, email, first_name, last_name, created_at) values (?, ?, 'U', 'V', now())",
                id, id + "@test.com");
        // The role lives only in the token; without one the test states the account's hat here.
        return TestHats.grant(userRepository.findById(id).orElseThrow(), UserRole.valueOf(role));
    }
}
