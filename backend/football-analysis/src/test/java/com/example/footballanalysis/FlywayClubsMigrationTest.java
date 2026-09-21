package com.example.footballanalysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Flyway V2 clubs and teams.club_id")
class FlywayClubsMigrationTest {

    @Test
    void addsClubsTableAndClubIdColumnOnExistingTeams() throws Exception {
        String url = "jdbc:h2:mem:flyway_clubs_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            connection.createStatement().execute("""
                    CREATE TABLE teams (
                        id UUID PRIMARY KEY,
                        name VARCHAR(255) NOT NULL
                    )
                    """);

            String sql = new String(
                    FlywayClubsMigrationTest.class
                            .getResourceAsStream("/db/migration/V2__clubs_and_team_club_id.sql")
                            .readAllBytes(),
                    StandardCharsets.UTF_8);
            for (String statement : sql.split(";")) {
                String executable = statement.lines()
                        .map(String::strip)
                        .filter(line -> !line.isEmpty() && !line.startsWith("--"))
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("");
                if (executable.isEmpty()) {
                    continue;
                }
                connection.createStatement().execute(executable);
            }

            try (ResultSet clubs = connection.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_name) = 'clubs'")) {
                clubs.next();
                assertThat(clubs.getInt(1)).isEqualTo(1);
            }
            try (ResultSet clubId = connection.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE lower(table_name) = 'teams' AND lower(column_name) = 'club_id'")) {
                clubId.next();
                assertThat(clubId.getInt(1)).isEqualTo(1);
            }
        }
    }
}
