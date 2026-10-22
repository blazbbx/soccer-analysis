package com.example.footballanalysis.perf;

import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.service.ChatService;
import com.example.footballanalysis.service.ClipService;
import com.example.footballanalysis.service.ClubService;
import com.example.footballanalysis.service.CupService;
import com.example.footballanalysis.service.CupStandingsService;
import com.example.footballanalysis.service.MatchService;
import com.example.footballanalysis.service.MeService;
import com.example.footballanalysis.service.PlayerParentService;
import com.example.footballanalysis.service.TeamEventService;
import com.example.footballanalysis.service.TeamService;
import com.example.footballanalysis.service.UserAccessService;
import com.example.footballanalysis.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Times the read paths against a large generated database (src/test/resources/perf/perf-data.sql).
 * Runs only with -Dperf.db.url=jdbc:postgresql://... ; the database needs pg_stat_statements.
 * Setup and results: docs/teljesitmenymeres.md.
 */
@EnabledIfSystemProperty(named = "perf.db.url", matches = ".+")
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
class QueryPerformanceIT {

    private static final int RUNS = 3;

    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private RabbitTemplate rabbitTemplate;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserAccessService userAccessService;
    @Autowired private TeamService teamService;
    @Autowired private ClubService clubService;
    @Autowired private CupService cupService;
    @Autowired private CupStandingsService cupStandingsService;
    @Autowired private MatchService matchService;
    @Autowired private ClipService clipService;
    @Autowired private ChatService chatService;
    @Autowired private TeamEventService teamEventService;
    @Autowired private UserService userService;
    @Autowired private MeService meService;
    @Autowired private PlayerParentService playerParentService;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("perf.db.url"));
        registry.add("spring.datasource.username", () -> System.getProperty("perf.db.user", "postgres"));
        registry.add("spring.datasource.password", () -> System.getProperty("perf.db.password", "x"));
    }

    private final List<String> rows = new ArrayList<>();

    @Test
    void measureReadPaths() {
        Jwt admin = jwt(uid("admin", 1));
        Jwt clubAdmin = jwt(uid("club_admin", 1));
        Jwt coach = jwt(uid("coach", 1));
        Jwt player = jwt(uid("player", 1));
        Jwt parent = jwt(uid("parent", 1));
        Jwt fan = jwt(uid("fan", 1));
        User adminUser = userAccessService.resolveCurrentUser(admin);
        User clubAdminUser = userAccessService.resolveCurrentUser(clubAdmin);
        User coachUser = userAccessService.resolveCurrentUser(coach);
        User parentUser = userAccessService.resolveCurrentUser(parent);
        User fanUser = userAccessService.resolveCurrentUser(fan);
        UUID team1 = uid("team", 1);
        UUID club1 = uid("club", 1);
        UUID cup1 = uid("cup", 1);
        UUID match1 = uid("match", 1);
        UUID event = uid("event", 1 + 85 * 3000);
        // The parent view lists only the teams of the selected children (profiles 1 and 30001).
        List<UUID> parentTeams = List.of(team1, uid("team", 1501));

        measure("me/views parent", () -> meService.getViews(parent));
        measure("my teams COACH", () -> teamService.getMyTeams(coach, UserRole.COACH));
        measure("my teams PARENT", () -> teamService.getMyTeams(parent, UserRole.PARENT));
        measure("my teams FAN", () -> teamService.getMyTeams(fan, UserRole.FAN));
        measure("my teams PLAYER", () -> teamService.getMyTeams(player, UserRole.PLAYER));
        measure("my teams CLUB_ADMIN", () -> teamService.getMyTeams(clubAdmin, UserRole.CLUB_ADMIN));
        measure("my teams ADMIN (3000 teams)", () -> teamService.getMyTeams(admin, UserRole.ADMIN));
        measure("team catalog ADMIN", () -> teamService.listCatalog(admin, UserRole.ADMIN, null, 1, 20));
        measure("team catalog ADMIN search", () -> teamService.listCatalog(admin, UserRole.ADMIN, "u15", 1, 20));
        measure("team catalog FAN search", () -> teamService.listCatalog(fan, UserRole.FAN, "team 1", 1, 20));
        measure("team detail coach", () -> teamService.getTeam(team1, coachUser));
        measure("clubs ADMIN", () -> clubService.listClubs(admin, 1, 20));
        measure("clubs CLUB_ADMIN", () -> clubService.listClubs(clubAdmin, 1, 20));
        measure("club teams", () -> clubService.listClubTeams(club1, clubAdmin));
        measure("club users", () -> clubService.listClubUsers(club1, null, 1, 20, clubAdmin));
        measure("club users search", () -> clubService.listClubUsers(club1, "kovács", 1, 20, clubAdmin));
        measure("cups ADMIN", () -> cupService.listAllCups(adminUser, UserRole.ADMIN, null, 1, 20));
        measure("cups COACH", () -> cupService.listAllCups(coachUser, UserRole.COACH, null, 1, 20));
        measure("cups PARENT", () -> cupService.listAllCups(parentUser, UserRole.PARENT, parentTeams, 1, 20));
        measure("cups FAN", () -> cupService.listAllCups(fanUser, UserRole.FAN, null, 1, 20));
        measure("cup detail", () -> cupService.getCup(cup1, adminUser, UserRole.ADMIN));
        measure("cup matches", () -> cupService.listMatches(cup1, adminUser, UserRole.ADMIN));
        measure("cup standings", () -> cupStandingsService.calculateStandings(cup1));
        measure("matches ADMIN", () -> matchService.getAllMatches(adminUser, UserRole.ADMIN, null, 1, 20));
        measure("matches ADMIN page 500", () -> matchService.getAllMatches(adminUser, UserRole.ADMIN, null, 501, 20));
        measure("matches COACH", () -> matchService.getAllMatches(coachUser, UserRole.COACH, null, 1, 20));
        measure("matches PARENT", () -> matchService.getAllMatches(parentUser, UserRole.PARENT, parentTeams, 1, 20));
        measure("matches FAN", () -> matchService.getAllMatches(fanUser, UserRole.FAN, null, 1, 20));
        measure("matches CLUB_ADMIN", () -> matchService.getAllMatches(clubAdminUser, UserRole.CLUB_ADMIN, null, 1, 20));
        measure("match detail", () -> matchService.getMatchDetails(match1, coachUser));
        measure("clips of match", () -> clipService.getClipsForMatch(match1, coachUser, 1, 20));
        measure("chat history", () -> chatService.getHistory(team1, coachUser, null, null, 50));
        measure("events list coach", () -> teamEventService.listEvents(team1, coachUser));
        measure("events list parent", () -> teamEventService.listEvents(team1, parentUser));
        measure("events past page", () -> teamEventService.listPastEvents(team1, null, null, 20, coachUser));
        measure("event detail", () -> teamEventService.getEvent(team1, event, parentUser));
        measure("users ADMIN", () -> userService.getAllUsers(1, 20, null));
        measure("users ADMIN search", () -> userService.getAllUsers(1, 20, "kovács"));
        measure("users ADMIN page 3000", () -> userService.getAllUsers(3001, 20, null));
        measure("children of parent", () -> playerParentService.listChildren(parentUser));

        System.out.println();
        System.out.println(String.format("%-30s %9s %9s %8s %8s", "scenario", "avg ms", "max ms", "sql", "db ms"));
        rows.forEach(System.out::println);
        System.out.println();
        System.out.println("Slowest statements overall (mean ms, calls):");
        jdbc.queryForList("""
                select round(mean_exec_time::numeric, 2) mean, calls, left(regexp_replace(query, '\\s+', ' ', 'g'), 230) q
                from pg_stat_statements
                where query not ilike '%pg_stat_statements%'
                order by mean_exec_time desc limit 15
                """).forEach(row -> System.out.println(row.get("mean") + " ms x" + row.get("calls") + "  " + row.get("q")));
    }

    private void measure(String name, Supplier<?> call) {
        try {
            call.get();
            jdbc.execute("select pg_stat_statements_reset()");
            long total = 0;
            long max = 0;
            for (int i = 0; i < RUNS; i++) {
                long start = System.nanoTime();
                call.get();
                long elapsed = (System.nanoTime() - start) / 1_000_000;
                total += elapsed;
                max = Math.max(max, elapsed);
            }
            Map<String, Object> stats = jdbc.queryForMap("""
                    select coalesce(sum(calls), 0) calls, coalesce(round(sum(total_exec_time)::numeric, 1), 0) ms
                    from pg_stat_statements
                    where query not ilike '%pg_stat_statements%'
                    """);
            long sql = ((Number) stats.get("calls")).longValue() / RUNS;
            double dbMs = ((Number) stats.get("ms")).doubleValue() / RUNS;
            rows.add(String.format("%-30s %9d %9d %8d %8.1f", name, total / RUNS, max, sql, dbMs));
            String detail = System.getProperty("perf.detail", "");
            if (!detail.isEmpty() && name.contains(detail)) {
                rows.add("  statements of '" + name + "' (calls per run, mean ms):");
                jdbc.queryForList("""
                        select calls, round(mean_exec_time::numeric, 3) mean, left(regexp_replace(query, '\s+', ' ', 'g'), 260) q
                        from pg_stat_statements where query not ilike '%pg_stat_statements%'
                        order by calls desc, total_exec_time desc
                        """).forEach(row -> rows.add("    x" + ((Number) row.get("calls")).longValue() / RUNS
                        + "  " + row.get("mean") + " ms  " + row.get("q")));
            }
        } catch (RuntimeException ex) {
            rows.add(String.format("%-30s FAILED: %s", name, ex));
        }
    }

    private static Jwt jwt(UUID subject) {
        return Jwt.withTokenValue("perf")
                .header("alg", "none")
                .subject(subject.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
    }

    /** Same ids as the generator: md5(kind || ':' || n)::uuid. */
    private static UUID uid(String kind, long n) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest((kind + ":" + n).getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(hash);
            return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                    + "-" + hex.substring(16, 20) + "-" + hex.substring(20));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
