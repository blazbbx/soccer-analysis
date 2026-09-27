package com.example.footballanalysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Flyway V4 player_profiles")
class FlywayPlayerProfilesMigrationTest {

    private static final UUID CLUB_ID = UUID.fromString("01000000-0000-0000-0000-000000000001");
    private static final UUID TEAM_A = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TEAM_B = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID TEAM_WITHOUT_CLUB = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID PLAYER_TWO_TEAMS = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID PLAYER_NO_CLUB = UUID.fromString("50000000-0000-0000-0000-000000000005");

    @Test
    void copiesOneProfilePerUserAndSkipsTeamsWithoutClub() throws Exception {
        String url = "jdbc:h2:mem:flyway_profiles_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            connection.createStatement().execute("""
                    CREATE TABLE clubs (
                        id UUID PRIMARY KEY,
                        name VARCHAR(255) NOT NULL
                    )
                    """);
            connection.createStatement().execute("""
                    CREATE TABLE teams (
                        id UUID PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        club_id UUID
                    )
                    """);
            connection.createStatement().execute("""
                    CREATE TABLE users (
                        id UUID PRIMARY KEY,
                        first_name VARCHAR(255),
                        last_name VARCHAR(255)
                    )
                    """);
            connection.createStatement().execute("""
                    CREATE TABLE player_teams (
                        player_id UUID NOT NULL,
                        team_id UUID NOT NULL
                    )
                    """);

            insert(connection, "INSERT INTO clubs (id, name) VALUES (?, ?)", CLUB_ID, "Vasas");
            insert(connection, "INSERT INTO teams (id, name, club_id) VALUES (?, ?, ?)", TEAM_A, "U11", CLUB_ID);
            insert(connection, "INSERT INTO teams (id, name, club_id) VALUES (?, ?, ?)", TEAM_B, "U13", CLUB_ID);
            insert(connection, "INSERT INTO teams (id, name, club_id) VALUES (?, ?, ?)", TEAM_WITHOUT_CLUB, "Orphan", null);
            insert(connection, "INSERT INTO users (id, first_name, last_name) VALUES (?, ?, ?)", PLAYER_TWO_TEAMS, "Peti", "Kiss");
            insert(connection, "INSERT INTO users (id, first_name, last_name) VALUES (?, ?, ?)", PLAYER_NO_CLUB, "No", "Club");
            insert(connection, "INSERT INTO player_teams (player_id, team_id) VALUES (?, ?)", PLAYER_TWO_TEAMS, TEAM_B);
            insert(connection, "INSERT INTO player_teams (player_id, team_id) VALUES (?, ?)", PLAYER_TWO_TEAMS, TEAM_A);
            insert(connection, "INSERT INTO player_teams (player_id, team_id) VALUES (?, ?)", PLAYER_NO_CLUB, TEAM_WITHOUT_CLUB);

            String sql = new String(
                    FlywayPlayerProfilesMigrationTest.class
                            .getResourceAsStream("/db/migration/V4__player_profiles.sql")
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
                    "SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_name) = 'player_profiles'")) {
                table.next();
                assertThat(table.getInt(1)).isEqualTo(1);
            }
            try (ResultSet index = connection.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM information_schema.indexes WHERE lower(index_name) = 'ix_player_profiles_team_id'")) {
                index.next();
                assertThat(index.getInt(1)).isGreaterThanOrEqualTo(1);
            }
            try (PreparedStatement profiles = connection.prepareStatement(
                    "SELECT team_id, first_name FROM player_profiles WHERE linked_user_id = ?")) {
                profiles.setObject(1, PLAYER_TWO_TEAMS);
                try (ResultSet rows = profiles.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getObject("team_id", UUID.class)).isEqualTo(TEAM_A);
                    assertThat(rows.getString("first_name")).isEqualTo("Peti");
                    assertThat(rows.next()).isFalse();
                }
            }
            try (PreparedStatement skipped = connection.prepareStatement(
                    "SELECT COUNT(*) FROM player_profiles WHERE linked_user_id = ?")) {
                skipped.setObject(1, PLAYER_NO_CLUB);
                try (ResultSet rows = skipped.executeQuery()) {
                    rows.next();
                    assertThat(rows.getInt(1)).isZero();
                }
            }
        }
    }

    private static void insert(Connection connection, String sql, Object... args) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.executeUpdate();
        }
    }
}
