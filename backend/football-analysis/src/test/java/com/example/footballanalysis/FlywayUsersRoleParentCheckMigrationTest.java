package com.example.footballanalysis;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Flyway V7 users_role_check PARENT")
class FlywayUsersRoleParentCheckMigrationTest {

    @Test
    void allowsParentRoleAfterRewritingCheckConstraint() throws Exception {
        String url = "jdbc:h2:mem:flyway_users_role_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            connection.createStatement().execute("""
                    CREATE TABLE users (
                        id UUID PRIMARY KEY,
                        email VARCHAR(255) NOT NULL,
                        role VARCHAR(20),
                        user_type VARCHAR(31),
                        CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'PLAYER', 'COACH', 'FAN'))
                    )
                    """);
            connection.createStatement().execute("""
                    CREATE TABLE team_invitations (
                        id UUID PRIMARY KEY,
                        invited_role VARCHAR(20) NOT NULL,
                        created_by_user_role VARCHAR(20) NOT NULL,
                        CONSTRAINT team_invitations_invited_role_check
                            CHECK (invited_role IN ('ADMIN', 'PLAYER', 'COACH', 'FAN'))
                    )
                    """);

            String parentId = UUID.randomUUID().toString();
            assertThatThrownBy(() -> connection.createStatement().execute(
                    "INSERT INTO users (id, email, role, user_type) VALUES ('"
                            + parentId + "', 'parent@test.test', 'PARENT', 'PARENT')"))
                    .isInstanceOf(SQLException.class);

            String sql = new String(
                    FlywayUsersRoleParentCheckMigrationTest.class
                            .getResourceAsStream("/db/migration/V7__users_role_parent_check.sql")
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

            assertThat(Flyway.configure().locations("classpath:db/migration").load()).isNotNull();

            assertThatCode(() -> connection.createStatement().execute(
                    "INSERT INTO users (id, email, role, user_type) VALUES ('"
                            + parentId + "', 'parent@test.test', 'PARENT', 'PARENT')"))
                    .doesNotThrowAnyException();
            assertThatCode(() -> connection.createStatement().execute(
                    "INSERT INTO team_invitations (id, invited_role, created_by_user_role) VALUES ('"
                            + UUID.randomUUID() + "', 'PARENT', 'COACH')"))
                    .doesNotThrowAnyException();
        }
    }
}
