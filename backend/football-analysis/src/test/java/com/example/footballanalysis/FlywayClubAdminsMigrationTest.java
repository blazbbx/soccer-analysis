package com.example.footballanalysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Flyway club admin role and club_admins")
class FlywayClubAdminsMigrationTest {

    @Test
    void allowsClubAdminRoleAndMembershipWithoutOpeningTeamInvites() throws Exception {
        String url = "jdbc:h2:mem:flyway_club_admin_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            connection.createStatement().execute("""
                    CREATE TABLE users (
                        id UUID PRIMARY KEY,
                        email VARCHAR(255) NOT NULL,
                        role VARCHAR(20),
                        user_type VARCHAR(31),
                        CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'PLAYER', 'COACH', 'FAN', 'PARENT')),
                        CONSTRAINT users_user_type_check CHECK (user_type IN ('ADMIN', 'PLAYER', 'COACH', 'FAN', 'PARENT'))
                    )
                    """);
            connection.createStatement().execute("""
                    CREATE TABLE clubs (
                        id UUID PRIMARY KEY,
                        name VARCHAR(255) NOT NULL
                    )
                    """);
            connection.createStatement().execute("""
                    CREATE TABLE team_invitations (
                        id UUID PRIMARY KEY,
                        invited_role VARCHAR(20) NOT NULL,
                        created_by_user_role VARCHAR(20) NOT NULL,
                        CONSTRAINT team_invitations_invited_role_check
                            CHECK (invited_role IN ('ADMIN', 'PLAYER', 'COACH', 'FAN', 'PARENT'))
                    )
                    """);

            String userId = UUID.randomUUID().toString();
            assertThatThrownBy(() -> connection.createStatement().execute(
                    "INSERT INTO users (id, email, role, user_type) VALUES ('"
                            + userId + "', 'clubadmin@test.test', 'CLUB_ADMIN', 'CLUB_ADMIN')"))
                    .isInstanceOf(SQLException.class);

            executeScript(connection, "/db/migration/V11__club_admin_role_check.sql");

            assertThatCode(() -> connection.createStatement().execute(
                    "INSERT INTO users (id, email, role, user_type) VALUES ('"
                            + userId + "', 'clubadmin@test.test', 'CLUB_ADMIN', 'CLUB_ADMIN')"))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> connection.createStatement().execute(
                    "INSERT INTO team_invitations (id, invited_role, created_by_user_role) VALUES ('"
                            + UUID.randomUUID() + "', 'CLUB_ADMIN', 'ADMIN')"))
                    .isInstanceOf(SQLException.class);

            String clubId = UUID.randomUUID().toString();
            connection.createStatement().execute(
                    "INSERT INTO clubs (id, name) VALUES ('" + clubId + "', 'Vasas')");
            executeScript(connection, "/db/migration/V12__club_admins.sql");

            assertThatCode(() -> connection.createStatement().execute(
                    "INSERT INTO club_admins (user_id, club_id) VALUES ('" + userId + "', '" + clubId + "')"))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> connection.createStatement().execute(
                    "INSERT INTO club_admins (user_id, club_id) VALUES ('" + userId + "', '" + clubId + "')"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private static void executeScript(Connection connection, String resource) throws Exception {
        String sql = new String(
                FlywayClubAdminsMigrationTest.class.getResourceAsStream(resource).readAllBytes(),
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
    }
}
