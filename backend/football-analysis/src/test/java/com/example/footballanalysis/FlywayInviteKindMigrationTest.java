package com.example.footballanalysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Flyway V6 invite_kind")
class FlywayInviteKindMigrationTest {

    @Test
    void backfillsKindFromInvitedRole() throws Exception {
        String url = "jdbc:h2:mem:flyway_invite_kind_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            connection.createStatement().execute("""
                    CREATE TABLE team_invitations (
                        id UUID PRIMARY KEY,
                        invited_role VARCHAR(20) NOT NULL
                    )
                    """);
            connection.createStatement().execute(
                    "INSERT INTO team_invitations (id, invited_role) VALUES ('00000000-0000-0000-0000-000000000001', 'PLAYER')");
            connection.createStatement().execute(
                    "INSERT INTO team_invitations (id, invited_role) VALUES ('00000000-0000-0000-0000-000000000002', 'PARENT')");

            String sql = new String(
                    FlywayInviteKindMigrationTest.class
                            .getResourceAsStream("/db/migration/V6__invite_kind.sql")
                            .readAllBytes(),
                    StandardCharsets.UTF_8);
            for (String statement : sql.split(";")) {
                String executable = statement.lines()
                        .map(String::strip)
                        .filter(line -> !line.isEmpty() && !line.startsWith("--"))
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("");
                if (!executable.isEmpty()) {
                    connection.createStatement().execute(executable);
                }
            }

            try (ResultSet rows = connection.createStatement().executeQuery(
                    "SELECT invited_role, invite_kind FROM team_invitations ORDER BY invited_role")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("invited_role")).isEqualTo("PARENT");
                assertThat(rows.getString("invite_kind")).isEqualTo("PARENT");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("invited_role")).isEqualTo("PLAYER");
                assertThat(rows.getString("invite_kind")).isEqualTo("PLAYER_SELF");
            }
        }
    }
}
