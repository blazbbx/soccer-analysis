package com.example.footballanalysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Flyway V19 team events")
class FlywayTeamEventsMigrationTest {

    @Test
    void createsTravelTablesUniquesAndCompositeOfferForeignKey() throws Exception {
        String url = "jdbc:h2:mem:flyway_team_events_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            connection.createStatement().execute("CREATE TABLE users (id UUID PRIMARY KEY)");
            connection.createStatement().execute("CREATE TABLE teams (id UUID PRIMARY KEY)");
            connection.createStatement().execute("CREATE TABLE player_profiles (id UUID PRIMARY KEY)");
            connection.createStatement().execute("CREATE TABLE matches (id UUID PRIMARY KEY)");
            connection.createStatement().execute("CREATE TABLE cup_matches (id UUID PRIMARY KEY)");
            runScript(connection, "/db/migration/V19__team_events.sql");

            assertThat(tableExists(connection, "team_events")).isTrue();
            assertThat(tableExists(connection, "event_attendances")).isTrue();
            assertThat(tableExists(connection, "ride_offers")).isTrue();
            assertThat(tableExists(connection, "ride_seats")).isTrue();
            assertThat(uniqueExists(connection, "ux_ride_seats_event_profile")).isTrue();
            assertThat(uniqueExists(connection, "ux_ride_offers_id_event")).isTrue();
            assertThat(uniqueExists(connection, "ux_ride_offers_event_driver")).isTrue();

            Set<String> offerEventColumns = foreignKeyColumns(connection, "fk_ride_seats_offer_event");
            assertThat(offerEventColumns).containsExactlyInAnyOrder("offer_id", "event_id");

            Set<String> singleOfferForeignKeys = new HashSet<>();
            try (ResultSet keys = connection.createStatement().executeQuery("""
                    SELECT tc.constraint_name
                    FROM information_schema.table_constraints tc
                    WHERE tc.constraint_type = 'FOREIGN KEY'
                      AND lower(tc.table_name) = 'ride_seats'
                    """)) {
                while (keys.next()) {
                    String name = keys.getString(1);
                    Set<String> columns = foreignKeyColumns(connection, name);
                    if (columns.equals(Set.of("offer_id"))) {
                        singleOfferForeignKeys.add(name.toLowerCase());
                    }
                }
            }
            assertThat(singleOfferForeignKeys).isEmpty();

            assertBooleanDefaultFalse(connection, "player_profiles", "can_offer_rides");
            assertBooleanDefaultFalse(connection, "event_attendances", "wants_ride");
            assertThat(indexExists(connection, "ix_ride_seats_profile")).isTrue();
            assertThat(indexExists(connection, "ix_event_attendances_profile")).isTrue();
        }
    }

    private static void assertBooleanDefaultFalse(Connection connection, String table, String column) throws Exception {
        try (ResultSet rows = connection.createStatement().executeQuery("""
                SELECT is_nullable, column_default
                FROM information_schema.columns
                WHERE lower(table_name) = '%s'
                  AND lower(column_name) = '%s'
                """.formatted(table, column))) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualToIgnoringCase("NO");
            assertThat(rows.getString(2).toLowerCase()).contains("false");
        }
    }

    private static boolean indexExists(Connection connection, String name) throws Exception {
        try (ResultSet rows = connection.createStatement().executeQuery(
                "SELECT COUNT(*) FROM information_schema.indexes WHERE lower(index_name) = '" + name + "'")) {
            rows.next();
            return rows.getInt(1) > 0;
        }
    }

    private static void runScript(Connection connection, String classpath) throws Exception {
        String sql = new String(
                FlywayTeamEventsMigrationTest.class.getResourceAsStream(classpath).readAllBytes(),
                StandardCharsets.UTF_8);
        for (String statement : sql.split(";")) {
            String executable = statement.lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("--"))
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
            if (!executable.isEmpty()) {
                connection.createStatement().execute(executable);
            }
        }
    }

    private static boolean tableExists(Connection connection, String table) throws Exception {
        try (ResultSet tables = connection.createStatement().executeQuery(
                "SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_name) = '" + table + "'")) {
            tables.next();
            return tables.getInt(1) == 1;
        }
    }

    private static boolean uniqueExists(Connection connection, String name) throws Exception {
        try (ResultSet constraints = connection.createStatement().executeQuery("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_type IN ('UNIQUE', 'PRIMARY KEY')
                  AND lower(constraint_name) = '%s'
                """.formatted(name.toLowerCase()))) {
            constraints.next();
            return constraints.getInt(1) == 1;
        }
    }

    private static Set<String> foreignKeyColumns(Connection connection, String constraintName) throws Exception {
        Set<String> columns = new HashSet<>();
        try (ResultSet rows = connection.createStatement().executeQuery("""
                SELECT lower(column_name) FROM information_schema.key_column_usage
                WHERE lower(constraint_name) = '%s'
                """.formatted(constraintName.toLowerCase()))) {
            while (rows.next()) {
                columns.add(rows.getString(1));
            }
        }
        return columns;
    }
}
