package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A felhasználókeresés valódi PostgreSQL-en, a V15 trigram indexeivel: teljes név mindkét sorrendben,
 * e-mail, a klub határa, a lapozás előtti darabszám, és hogy a feltétel tényleg az indexeket használja.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserSearchPostgresTest {

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

    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void globalSearchFindsTheFullNameInBothOrdersAndTheEmail() {
        String tag = tag();
        User kovacs = insertUser("Kovács" + tag, "Péter", "pk" + tag + "@example.com");
        insertUser("Nagy" + tag, "Anna", "na" + tag + "@example.com");

        assertThat(globalIds("kovács" + tag + " péter")).containsExactly(kovacs.getId());
        assertThat(globalIds("péter kovács" + tag)).containsExactly(kovacs.getId());
        assertThat(globalIds("vács" + tag + " pé")).containsExactly(kovacs.getId());
        assertThat(globalIds("pk" + tag + "@exa")).containsExactly(kovacs.getId());
        assertThat(globalIds("nincs" + tag)).isEmpty();
    }

    @Test
    void clubSearchStaysInsideTheClubAndCountsBeforePaging() {
        String tag = tag();
        UUID club = insertClub("Klub" + tag);
        UUID otherClub = insertClub("Másik" + tag);
        UUID team = insertTeam("U12" + tag, club);
        UUID otherTeam = insertTeam("U9" + tag, otherClub);

        User coach = insertUser("Szabó" + tag, "Béla", "sb" + tag + "@example.com");
        User fan = insertUser("Szabó" + tag, "Cili", "sc" + tag + "@example.com");
        User outsider = insertUser("Szabó" + tag, "Dénes", "sd" + tag + "@example.com");
        jdbc.update("insert into coach_teams (coach_id, team_id) values (?, ?)", coach.getId(), team);
        jdbc.update("insert into fan_favorite_teams (fan_id, team_id) values (?, ?)", fan.getId(), team);
        jdbc.update("insert into coach_teams (coach_id, team_id) values (?, ?)", outsider.getId(), otherTeam);

        String pattern = "%szabó" + tag + "%";
        List<UserRepository.ClubMemberRow> first = userRepository.findClubMemberPage(club, pattern, 1, 0);
        List<UserRepository.ClubMemberRow> second = userRepository.findClubMemberPage(club, pattern, 1, 1);

        assertThat(first).extracting(UserRepository.ClubMemberRow::getId).containsExactly(coach.getId());
        assertThat(first.get(0).getTotal()).isEqualTo(2L);
        assertThat(second).extracting(UserRepository.ClubMemberRow::getId).containsExactly(fan.getId());
        assertThat(userRepository.findClubMemberPage(club, pattern, 1, 2)).isEmpty();
        assertThat(userRepository.countUsersInClub(club, pattern)).isEqualTo(2L);

        // A csapatnév már nem keresési feltétel: az U12 csapat tagjai erre nem jönnek.
        assertThat(userRepository.findClubMemberPage(club, "%u12" + tag + "%", 20, 0)).isEmpty();
        assertThat(userRepository.findClubMemberPage(club, "%béla szabó" + tag + "%", 20, 0))
                .extracting(UserRepository.ClubMemberRow::getId).containsExactly(coach.getId());
    }

    @Test
    void theSearchConditionUsesTheTrigramIndexes() {
        String sql = "SELECT u.id FROM users u WHERE "
                + UserSearchSql.NAME_OR_EMAIL_MATCH.replace(":pattern", "'%kov%'");
        String plan = jdbc.execute((ConnectionCallback<String>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET enable_seqscan = off");
                List<String> lines = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery("EXPLAIN " + sql)) {
                    while (rows.next()) {
                        lines.add(rows.getString(1));
                    }
                }
                statement.execute("RESET enable_seqscan");
                return String.join("\n", lines);
            }
        });

        assertThat(plan).contains("ix_users_full_name_trgm");
        assertThat(plan).contains("ix_users_full_name_reversed_trgm");
        assertThat(plan).contains("ix_users_email_trgm");
    }

    private List<UUID> globalIds(String needle) {
        return userRepository.findPageMatching("%" + needle + "%", PageRequest.of(0, 20)).getContent().stream()
                .map(User::getId)
                .toList();
    }

    private User insertUser(String lastName, String firstName, String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, email, first_name, last_name, created_at, role, user_type)"
                        + " values (?, ?, ?, ?, now(), 'FAN', 'FAN')",
                id, email, firstName, lastName);
        return userRepository.findById(id).orElseThrow();
    }

    private UUID insertClub(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into clubs (id, name) values (?, ?)", id, name);
        return id;
    }

    private UUID insertTeam(String name, UUID clubId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into teams (id, name, club_id) values (?, ?, ?)", id, name, clubId);
        return id;
    }

    /** Tesztenként egyedi, kisbetűs utótag, hogy a tesztek adatai ne keveredjenek a közös konténerben. */
    private static String tag() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
