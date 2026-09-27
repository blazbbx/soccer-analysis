package com.example.footballanalysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Flyway V5 player_parents")
class FlywayPlayerParentsMigrationTest {

    @Test
    void addsParentTableAndInviteProfileColumn() throws Exception {
        String url = "jdbc:h2:mem:flyway_parents_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            connection.createStatement().execute("CREATE TABLE users (id UUID PRIMARY KEY)");
            connection.createStatement().execute("CREATE TABLE clubs (id UUID PRIMARY KEY)");
            connection.createStatement().execute("CREATE TABLE teams (id UUID PRIMARY KEY)");
            connection.createStatement().execute("""
                    CREATE TABLE player_profiles (
                        id UUID PRIMARY KEY,
                        club_id UUID NOT NULL
                    )
                    """);
            connection.createStatement().execute("""
                    CREATE TABLE team_invitations (
                        id UUID PRIMARY KEY,
                        team_id UUID NOT NULL
                    )
                    """);

            String sql = new String(
                    FlywayPlayerParentsMigrationTest.class
                            .getResourceAsStream("/db/migration/V5__player_parents.sql")
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

            try (ResultSet table = connection.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_name) = 'player_parents'")) {
                table.next();
                assertThat(table.getInt(1)).isEqualTo(1);
            }
            try (ResultSet column = connection.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE lower(table_name) = 'team_invitations' AND lower(column_name) = 'player_profile_id'")) {
                column.next();
                assertThat(column.getInt(1)).isEqualTo(1);
            }
        }
    }
}
