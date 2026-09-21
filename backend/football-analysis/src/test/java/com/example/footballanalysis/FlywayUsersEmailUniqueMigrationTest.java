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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Flyway V1 users.email unique index")
class FlywayUsersEmailUniqueMigrationTest {

    @Test
    void uniqueEmailIndexRejectsDuplicateInsert() throws Exception {
        String url = "jdbc:h2:mem:flyway_email_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            connection.createStatement().execute("""
                    CREATE TABLE users (
                        id UUID PRIMARY KEY,
                        email VARCHAR(255) NOT NULL
                    )
                    """);

            String sql = new String(
                    FlywayUsersEmailUniqueMigrationTest.class
                            .getResourceAsStream("/db/migration/V1__users_email_unique.sql")
                            .readAllBytes(),
                    StandardCharsets.UTF_8);
            connection.createStatement().execute(sql);

            assertThat(Flyway.configure().locations("classpath:db/migration").load()).isNotNull();

            connection.createStatement().execute(
                    "INSERT INTO users (id, email) VALUES ('" + UUID.randomUUID() + "', 'same@test.com')");

            assertThatThrownBy(() -> connection.createStatement().execute(
                    "INSERT INTO users (id, email) VALUES ('" + UUID.randomUUID() + "', 'same@test.com')"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
