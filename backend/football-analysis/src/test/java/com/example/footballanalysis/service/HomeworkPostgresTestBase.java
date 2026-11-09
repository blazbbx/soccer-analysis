package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.HomeworkItemType;
import com.example.footballanalysis.model.db.HomeworkQuestionKind;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.requests.HomeworkAnswerRequest;
import com.example.footballanalysis.model.requests.HomeworkItemRequest;
import com.example.footballanalysis.model.requests.PublishHomeworkRequest;
import com.example.footballanalysis.model.requests.SubmitAssignmentRequest;
import com.example.footballanalysis.model.requests.UpsertHomeworkRequest;
import com.example.footballanalysis.model.responses.AssignmentResponse;
import com.example.footballanalysis.model.responses.HomeworkDetailResponse;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import com.example.footballanalysis.repository.UserRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The homework services on a real PostgreSQL with real transactions and real access rules. Only the clock is a test
 * double. One container serves every subclass, because the cached Spring context keeps pointing at it.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import({HomeworkService.class, HomeworkContent.class, HomeworkRecipients.class, HomeworkViews.class, HomeworkGuard.class,
        HomeworkAssignmentViews.class, HomeworkAnswers.class, HomeworkReview.class, HomeworkComments.class,
        HomeworkAccess.class,
        UserAccessService.class, TeamMembershipQuery.class, AuditEventService.class, HomeworkPostgresTestBase.ClockConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
abstract class HomeworkPostgresTestBase {

    protected static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 0);

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    static final class MovableClock extends Clock {
        private Instant instant = NOW.toInstant(ZoneOffset.UTC);

        void set(LocalDateTime time) {
            instant = time.toInstant(ZoneOffset.UTC);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean @Primary MovableClock movableClock() { return new MovableClock(); }
    }

    @Autowired protected HomeworkService homeworkService;
    @Autowired protected HomeworkContent contentService;
    @Autowired protected HomeworkRecipients recipientService;
    @Autowired protected HomeworkAssignmentViews assignmentViews;
    @Autowired protected HomeworkAnswers answerService;
    @Autowired protected HomeworkReview reviewService;
    @Autowired protected HomeworkComments commentService;
    @Autowired protected UserAccessService userAccessService;
    @Autowired protected UserRepository userRepository;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MovableClock clock;

    protected UUID clubId;
    protected UUID teamId;
    protected User coach;

    @BeforeEach
    void baseFixtures() {
        clock.set(NOW);
        clubId = insertClub();
        teamId = insertTeam(clubId);
        coach = coachOn(teamId);
    }

    // Request builders

    protected static UpsertHomeworkRequest upsert(List<HomeworkItemRequest> items) {
        return new UpsertHomeworkRequest("Nézd meg a 2. gólt", null, null, null, items, false, null, null);
    }

    protected static UpsertHomeworkRequest upsert(LocalDateTime dueAt, Boolean allowLate, List<HomeworkItemRequest> items) {
        return new UpsertHomeworkRequest("Nézd meg a 2. gólt", null, dueAt, allowLate, items, false, null, null);
    }

    protected static HomeworkItemRequest text(String body) {
        return new HomeworkItemRequest(null, null, HomeworkItemType.TEXT, body, null, null, null, null, null, null, null, null);
    }

    protected static HomeworkItemRequest question(String body, Boolean required) {
        return new HomeworkItemRequest(null, null, HomeworkItemType.QUESTION, body, null, null, HomeworkQuestionKind.FREE_TEXT, null, null, required, null, null);
    }

    protected static HomeworkItemRequest choice(HomeworkQuestionKind kind, List<String> options, List<Integer> correct) {
        return new HomeworkItemRequest(null, null, HomeworkItemType.QUESTION, "Melyik?", null, null, kind, options, correct, null, null, null);
    }

    protected static HomeworkItemRequest clipItem(UUID clipId) {
        return new HomeworkItemRequest(null, null, HomeworkItemType.CLIP, null, clipId, null, null, null, null, null, null, null);
    }

    protected static HomeworkItemRequest keyedClip(String key, UUID clipId) {
        return new HomeworkItemRequest(null, key, HomeworkItemType.CLIP, null, clipId, null, null, null, null, null, null, null);
    }

    protected static HomeworkItemRequest timestamp(String refKey, Boolean required) {
        return new HomeworkItemRequest(null, null, HomeworkItemType.QUESTION, "Hol?", null, null, HomeworkQuestionKind.TIMESTAMP, null, null, required, null, refKey);
    }

    protected HomeworkDetailResponse publishNow(UpsertHomeworkRequest request, UUID... profiles) {
        HomeworkDetailResponse draft = homeworkService.create(teamId, request, coach);
        return homeworkService.publish(teamId, draft.id(), new PublishHomeworkRequest(false, List.of(profiles), draft.updatedAt()), coach);
    }

    /** Hands in the whole answer at once, against the homework as it is now. */
    protected AssignmentResponse handIn(UUID assignmentId, User actor, String text, List<HomeworkAnswerRequest> answers) {
        return answerService.submit(assignmentId, new SubmitAssignmentRequest(text, answers, homeworkUpdatedAt(assignmentId)), actor);
    }

    protected LocalDateTime homeworkUpdatedAt(UUID assignmentId) {
        return jdbc.queryForObject("""
                select homework.updated_at from homeworks homework
                join homework_assignments assignment on assignment.homework_id = homework.id
                where assignment.id = ?
                """, LocalDateTime.class, assignmentId);
    }

    // Fixtures

    protected UUID assignmentOf(UUID homeworkId, UUID profileId) {
        return jdbc.queryForObject("select id from homework_assignments where homework_id = ? and player_profile_id = ?",
                UUID.class, homeworkId, profileId);
    }

    /** {@code handedIn} sets the hand-in time the way a real hand-in does, so the rules that look at it see one. */
    protected void setStatus(UUID assignmentId, String status, int handedIn) {
        jdbc.update("update homework_assignments set status = ?, submitted_at = case when ? then localtimestamp end where id = ?",
                status, handedIn > 0, assignmentId);
    }

    protected LocalDateTime column(String sql, UUID id) {
        return jdbc.queryForObject(sql, LocalDateTime.class, id);
    }

    protected UUID insertClub() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into clubs (id, name, created_at) values (?, ?, now())", id, "Club " + id);
        return id;
    }

    protected UUID insertTeam(UUID club) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into teams (id, name, club_id) values (?, ?, ?)", id, "Team " + id, club);
        return id;
    }

    protected UUID rosterProfile(UUID team, String firstName) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into player_profiles (id, first_name, last_name, created_at) values (?, ?, 'Kovacs', now())", id, firstName);
        jdbc.update("insert into team_roster (player_profile_id, team_id) values (?, ?)", id, team);
        return id;
    }

    protected void leaveRoster(UUID profileId) {
        jdbc.update("delete from team_roster where player_profile_id = ?", profileId);
    }

    protected void joinRoster(UUID profileId, UUID team) {
        jdbc.update("insert into team_roster (player_profile_id, team_id) values (?, ?)", profileId, team);
    }

    protected User insertUser(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, email, first_name, last_name, created_at, role, user_type) values (?, ?, 'U', 'V', now(), ?, ?)",
                id, id + "@test.com", role, role);
        return userRepository.findById(id).orElseThrow();
    }

    protected User coachOn(UUID team) {
        User user = insertUser("COACH");
        jdbc.update("insert into coach_teams (coach_id, team_id) values (?, ?)", user.getId(), team);
        return user;
    }

    protected User clubAdminOf(UUID club) {
        User user = insertUser("CLUB_ADMIN");
        jdbc.update("insert into club_admins (user_id, club_id) values (?, ?)", user.getId(), club);
        return user;
    }

    protected User linkedPlayer(UUID profileId) {
        User user = insertUser("PLAYER");
        jdbc.update("update player_profiles set linked_user_id = ? where id = ?", user.getId(), profileId);
        return user;
    }

    protected User parentOf(UUID... profileIds) {
        User user = insertUser("PARENT");
        for (UUID profileId : profileIds) {
            jdbc.update("insert into player_parents (user_id, player_profile_id, created_at) values (?, ?, now())", user.getId(), profileId);
        }
        return user;
    }

    /** A clip of the team's own home match, uploaded by this user: the kind a homework may hold. */
    protected UUID clip(User uploader, String renderStatus) {
        UUID matchId = UUID.randomUUID();
        jdbc.update("insert into matches (id, uploaded_by_user_id, home_team_id) values (?, ?, ?)", matchId, uploader.getId(), teamId);
        return insertClip(matchId, renderStatus);
    }

    /** A clip of a match without a team, which only its uploader can open. */
    protected UUID foreignClip(User uploader, String renderStatus) {
        UUID matchId = UUID.randomUUID();
        jdbc.update("insert into matches (id, uploaded_by_user_id) values (?, ?)", matchId, uploader.getId());
        return insertClip(matchId, renderStatus);
    }

    private UUID insertClip(UUID matchId, String renderStatus) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into clips (id, match_id, name, created_at, render_status) values (?, ?, 'Clip', now(), ?)", id, matchId, renderStatus);
        return id;
    }

    protected static void assertCode(ThrowingCallable call, Class<? extends Throwable> type, String code) {
        assertThatThrownBy(call).isInstanceOf(type).hasFieldOrPropertyWithValue("messageCode", code);
    }
}
