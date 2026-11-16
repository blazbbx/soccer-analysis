package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.HomeworkAssignmentStatus;
import com.example.footballanalysis.model.db.HomeworkItemType;
import com.example.footballanalysis.model.db.HomeworkQuestionKind;
import com.example.footballanalysis.model.db.HomeworkStatus;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.AddHomeworkAssigneesRequest;
import com.example.footballanalysis.model.requests.HomeworkItemRequest;
import com.example.footballanalysis.model.requests.PublishHomeworkRequest;
import com.example.footballanalysis.model.requests.UpsertHomeworkRequest;
import com.example.footballanalysis.model.responses.AssignmentRowResponse;
import com.example.footballanalysis.model.responses.HomeworkDetailResponse;
import com.example.footballanalysis.model.responses.HomeworkItemResponse;
import com.example.footballanalysis.model.responses.HomeworkSummaryResponse;
import com.example.footballanalysis.model.responses.PageResponse;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import com.example.footballanalysis.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * On a real PostgreSQL with real transactions: the item diff depends on flush order, jsonb and the locks, which mocks
 * would hide. The access rules are the real ones too. Only the clock is a test double.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import({HomeworkService.class, HomeworkContent.class, HomeworkRecipients.class, HomeworkViews.class, HomeworkGuard.class,
        HomeworkAccess.class, UserAccessService.class, TestHats.class, TeamMembershipQuery.class, AuditEventService.class,
        HomeworkServiceTest.ClockConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HomeworkServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
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

    @Autowired private HomeworkService service;
    @Autowired private HomeworkRecipients recipientService;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MovableClock clock;

    private UUID clubId;
    private UUID teamId;
    private User coach;
    private UUID anna;
    private UUID bela;

    @BeforeEach
    void fixtures() {
        clock.set(NOW);
        clubId = insertClub();
        teamId = insertTeam(clubId);
        coach = coachOn(teamId);
        anna = rosterProfile(teamId, "Anna");
        bela = rosterProfile(teamId, "Bela");
    }

    // Writing a draft

    @Test
    void aDraftWithoutItemsSavesButDoesNotPublish() {
        HomeworkDetailResponse draft = service.create(teamId, upsert(List.of()), coach);

        assertThat(draft.status()).isEqualTo(HomeworkStatus.DRAFT);
        assertThat(draft.items()).isEmpty();
        assertCode(() -> service.publish(teamId, draft.id(), publishTo(draft, anna), coach),
                BadRequestException.class, "validation.homework.items");
    }

    @Test
    void omittedFlagsMeanTrueAndGivenOnesStay() {
        HomeworkDetailResponse defaults = service.create(teamId, upsert(List.of(question("Hol?", null))), coach);
        assertThat(defaults.allowLate()).isTrue();
        assertThat(defaults.items().get(0).required()).isTrue();

        HomeworkItemRequest optional = new HomeworkItemRequest(null, null, HomeworkItemType.QUESTION, "Hol?", null, null,
                HomeworkQuestionKind.FREE_TEXT, null, null, false, null, null);
        HomeworkDetailResponse given = service.create(teamId, new UpsertHomeworkRequest("T", null, null, false, List.of(optional), false, null, null), coach);
        assertThat(given.allowLate()).isFalse();
        assertThat(given.items().get(0).required()).isFalse();
    }

    @Test
    void theBlockRulesAreEnforced() {
        assertInvalidItem(new HomeworkItemRequest(null, null, HomeworkItemType.TEXT, " ", null, null, null, null, null, null, null, null));
        assertInvalidItem(new HomeworkItemRequest(null, null, HomeworkItemType.CLIP, null, null, null, null, null, null, null, null, null));
        assertInvalidItem(new HomeworkItemRequest(null, null, HomeworkItemType.QUESTION, null, null, null, HomeworkQuestionKind.FREE_TEXT, null, null, null, null, null));
        assertInvalidItem(new HomeworkItemRequest(null, null, HomeworkItemType.QUESTION, "Q", null, null, null, null, null, null, null, null));
        assertInvalidItem(choice(HomeworkQuestionKind.SINGLE_CHOICE, List.of("A"), List.of(0)));
        assertInvalidItem(choice(HomeworkQuestionKind.SINGLE_CHOICE, List.of("A", "B"), List.of(0, 1)));
        assertInvalidItem(choice(HomeworkQuestionKind.MULTI_CHOICE, List.of("A", "B"), List.of()));
        assertCode(() -> service.create(teamId, upsert(List.of(choice(HomeworkQuestionKind.SINGLE_CHOICE, List.of("A", "B"), List.of(2)))), coach),
                BadRequestException.class, "error.homework.invalid_option");

        HomeworkDetailResponse saved = service.create(teamId, upsert(List.of(choice(HomeworkQuestionKind.MULTI_CHOICE, List.of("A", "B", "C"), List.of(2, 0)))), coach);
        assertThat(saved.items().get(0).options()).containsExactly("A", "B", "C");
        assertThat(saved.items().get(0).correctOptions()).containsExactly(0, 2);
    }

    @Test
    void aTimestampQuestionResolvesAClipItemLaterInTheSameRequest() {
        UUID clipId = clip(coach, "COMPLETED");
        HomeworkItemRequest question = new HomeworkItemRequest(null, null, HomeworkItemType.QUESTION, "Hol?", null, null,
                HomeworkQuestionKind.TIMESTAMP, null, null, null, null, "c1");
        HomeworkItemRequest clip = new HomeworkItemRequest(null, "c1", HomeworkItemType.CLIP, null, clipId, 1200, null, null, null, null, null, null);

        HomeworkDetailResponse saved = service.create(teamId, upsert(List.of(question, clip)), coach);

        assertThat(saved.items()).extracting(HomeworkItemResponse::position).containsExactly(0, 1);
        assertThat(saved.items().get(0).refItemId()).isEqualTo(saved.items().get(1).id());
        assertThat(saved.items().get(1).clipRenderStatus()).isEqualTo("COMPLETED");
        assertThat(saved.items().get(1).focusMs()).isEqualTo(1200);

        // The saved reference still holds after a round trip by id.
        HomeworkDetailResponse again = service.update(teamId, saved.id(), upsertFor(saved, echo(saved.items())), coach);
        assertThat(again.items().get(0).refItemId()).isEqualTo(again.items().get(1).id());
    }

    @Test
    void aTimestampQuestionMustPointToExactlyOneClipItemOfThisHomework() {
        UUID clipId = clip(coach, "COMPLETED");
        HomeworkItemRequest clip = new HomeworkItemRequest(null, "c1", HomeworkItemType.CLIP, null, clipId, null, null, null, null, null, null, null);
        for (HomeworkItemRequest wrong : List.of(
                timestamp(null, null),
                timestamp(UUID.randomUUID(), "c1"),
                timestamp(UUID.randomUUID(), null),
                timestamp(null, "missing"))) {
            assertCode(() -> service.create(teamId, upsert(List.of(clip, wrong)), coach),
                    BadRequestException.class, "error.homework.timestamp_ref");
        }
        HomeworkItemRequest text = new HomeworkItemRequest(null, "t1", HomeworkItemType.TEXT, "x", null, null, null, null, null, null, null, null);
        assertCode(() -> service.create(teamId, upsert(List.of(text, timestamp(null, "t1"))), coach),
                BadRequestException.class, "error.homework.timestamp_ref");
    }

    @Test
    void anItemOfAnotherHomeworkIsRejected() {
        HomeworkDetailResponse other = service.create(teamId, upsert(List.of(text("x"))), coach);
        HomeworkDetailResponse mine = service.create(teamId, upsert(List.of(text("y"))), coach);
        HomeworkItemRequest stolen = new HomeworkItemRequest(other.items().get(0).id(), null, HomeworkItemType.TEXT, "z", null, null, null, null, null, null, null, null);

        assertCode(() -> service.update(teamId, mine.id(), upsertFor(mine, List.of(stolen)), coach),
                BadRequestException.class, "error.homework.invalid_item");
    }

    @Test
    void thePutReordersUpdatesInsertsAndDeletes() {
        HomeworkDetailResponse draft = service.create(teamId, upsert(List.of(text("a"), text("b"), text("c"))), coach);
        List<HomeworkItemResponse> items = draft.items();
        List<HomeworkItemRequest> next = List.of(
                edit(items.get(2), "c2"),
                text("new"),
                edit(items.get(0), "a"));

        HomeworkDetailResponse saved = service.update(teamId, draft.id(), upsertFor(draft, next), coach);

        assertThat(saved.items()).extracting(HomeworkItemResponse::body).containsExactly("c2", "new", "a");
        assertThat(saved.items().get(0).id()).isEqualTo(items.get(2).id());
        assertThat(saved.items().get(2).id()).isEqualTo(items.get(0).id());
        assertThat(jdbc.queryForObject("select count(*) from homework_items where homework_id = ?", Integer.class, draft.id())).isEqualTo(3);
    }

    @Test
    void onlyAClipOfTheTeamsOwnMatchCanBeWrittenIntoAnItem() {
        // The coach uploaded this match and plays its clips, but the match is not the team's.
        UUID uploadedByCoach = foreignClip(coach, "COMPLETED");
        assertCode(() -> service.create(teamId, upsert(List.of(clipItem(uploadedByCoach))), coach),
                ForbiddenException.class, "error.homework.foreign_clip");

        // An away match of the team counts as well.
        UUID away = UUID.randomUUID();
        jdbc.update("insert into matches (id, away_team_id) values (?, ?)", away, teamId);
        UUID awayClip = insertClip(away, "COMPLETED");
        assertThat(service.create(teamId, upsert(List.of(clipItem(awayClip))), coach).items()).hasSize(1);

        UUID foreign = clipOnTeamMatch(insertTeam(clubId), "COMPLETED");
        HomeworkItemRequest clip = new HomeworkItemRequest(null, null, HomeworkItemType.CLIP, null, foreign, null, null, null, null, null, null, null);
        assertCode(() -> service.create(teamId, upsert(List.of(clip)), coach), ForbiddenException.class, "error.homework.foreign_clip");

        UUID mine = clip(coach, "COMPLETED");
        HomeworkDetailResponse saved = service.create(teamId, upsert(List.of(clipItem(mine))), coach);
        HomeworkItemRequest rewritten = new HomeworkItemRequest(saved.items().get(0).id(), null, HomeworkItemType.CLIP, null, foreign, null, null, null, null, null, null, null);
        assertCode(() -> service.update(teamId, saved.id(), upsertFor(saved, List.of(rewritten)), coach),
                ForbiddenException.class, "error.homework.foreign_clip");

        assertCode(() -> service.create(teamId, upsert(List.of(clipItem(UUID.randomUUID()))), coach),
                NotFoundException.class, "error.homework.clip_not_found");
    }

    // Draft recipients

    @Test
    void draftRecipientsStayAcrossSavesAndGoAtPublish() {
        HomeworkDetailResponse draft = service.create(teamId, withRecipients(false, List.of(anna, bela)), coach);
        assertThat(draft.draftRecipientIds()).containsExactlyInAnyOrder(anna, bela);

        HomeworkDetailResponse kept = service.update(teamId, draft.id(), upsertFor(draft, echo(draft.items()), false, null), coach);
        assertThat(kept.draftRecipientIds()).containsExactlyInAnyOrder(anna, bela);

        HomeworkDetailResponse narrowed = service.update(teamId, draft.id(), upsertFor(kept, echo(kept.items()), false, List.of(bela)), coach);
        assertThat(narrowed.draftRecipientIds()).containsExactly(bela);

        HomeworkDetailResponse entire = service.update(teamId, draft.id(), upsertFor(narrowed, echo(narrowed.items()), true, List.of()), coach);
        assertThat(entire.draftEntireRoster()).isTrue();
        assertThat(entire.draftRecipientIds()).isEmpty();

        HomeworkDetailResponse published = service.publish(teamId, draft.id(), publishTo(entire, anna), coach);
        assertThat(published.draftEntireRoster()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from homework_draft_recipients where homework_id = ?", Integer.class, draft.id())).isZero();
    }

    @Test
    void recipientsMustBeCompatibleAndOnTheRoster() {
        assertCode(() -> service.create(teamId, withRecipients(true, List.of(anna)), coach),
                BadRequestException.class, "error.homework.invalid_recipients");
        UUID stranger = rosterProfile(insertTeam(clubId), "Stranger");
        assertCode(() -> service.create(teamId, withRecipients(false, List.of(stranger)), coach),
                NotFoundException.class, "error.player_profile.not_found");

        HomeworkDetailResponse draft = service.create(teamId, upsert(List.of(text("x"))), coach);
        assertCode(() -> service.publish(teamId, draft.id(), new PublishHomeworkRequest(true, List.of(anna), draft.updatedAt()), coach),
                BadRequestException.class, "error.homework.invalid_recipients");
        assertCode(() -> service.publish(teamId, draft.id(), new PublishHomeworkRequest(false, List.of(stranger), draft.updatedAt()), coach),
                NotFoundException.class, "error.player_profile.not_found");
        assertCode(() -> service.publish(teamId, draft.id(), new PublishHomeworkRequest(false, List.of(), draft.updatedAt()), coach),
                BadRequestException.class, "error.homework.no_assignees");
    }

    // Publishing

    @Test
    void publishingNowCreatesOneRowPerProfileAndWritesAnAudit() {
        HomeworkDetailResponse draft = service.create(teamId, upsert(List.of(text("Titkos szöveg"))), coach);

        HomeworkDetailResponse published = service.publish(teamId, draft.id(),
                new PublishHomeworkRequest(false, List.of(anna, bela, anna), draft.updatedAt()), coach);

        assertThat(published.status()).isEqualTo(HomeworkStatus.PUBLISHED);
        assertThat(column("select published_at from homeworks where id = ?", draft.id())).isEqualTo(NOW);
        List<AssignmentRowResponse> rows = recipientService.listAssignments(teamId, draft.id(), coach);
        assertThat(rows).extracting(AssignmentRowResponse::playerProfileId).containsExactlyInAnyOrder(anna, bela);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.status()).isEqualTo(HomeworkAssignmentStatus.ASSIGNED);
            assertThat(column("select assigned_at from homework_assignments where id = ?", row.id())).isEqualTo(NOW);
        });
        String details = jdbc.queryForObject(
                "select details from audit_events where event_type = 'HOMEWORK_PUBLISHED' and target_id = ?", String.class, draft.id().toString());
        assertThat(details).contains("teamId=" + teamId).contains("assigneeCount=2").doesNotContain("Titkos");
    }

    @Test
    void entireRosterMeansTheRosterAtPublishTime() {
        HomeworkDetailResponse draft = service.create(teamId, upsert(List.of(text("x"))), coach);
        UUID late = rosterProfile(teamId, "Late");

        service.publish(teamId, draft.id(), new PublishHomeworkRequest(true, null, draft.updatedAt()), coach);

        assertThat(recipientService.listAssignments(teamId, draft.id(), coach)).extracting(AssignmentRowResponse::playerProfileId)
                .containsExactlyInAnyOrder(anna, bela, late);
    }

    @Test
    void publishChecksTheWriteTheStateAndTheTimes() {
        HomeworkDetailResponse draft = service.create(teamId, upsert(List.of(text("x"))), coach);

        assertCode(() -> service.publish(teamId, draft.id(), new PublishHomeworkRequest(true, null, null), coach),
                BadRequestException.class, "validation.homework.expected_updated_at");
        assertCode(() -> service.publish(teamId, draft.id(), new PublishHomeworkRequest(true, null, draft.updatedAt().minusSeconds(1)), coach),
                ConflictException.class, "error.homework.stale_write");

        HomeworkDetailResponse pastDue = service.update(teamId, draft.id(), new UpsertHomeworkRequest("T", null, NOW.minusHours(1), null, echo(draft.items()), false, null, draft.updatedAt()), coach);
        assertCode(() -> service.publish(teamId, draft.id(), new PublishHomeworkRequest(true, null, pastDue.updatedAt()), coach),
                BadRequestException.class, "error.homework.due_before_publish");

        HomeworkDetailResponse dated = service.update(teamId, draft.id(), new UpsertHomeworkRequest("T", null, NOW.plusHours(1), null, echo(pastDue.items()), false, null, pastDue.updatedAt()), coach);
        HomeworkDetailResponse published = service.publish(teamId, draft.id(), new PublishHomeworkRequest(true, null, dated.updatedAt()), coach);
        assertThat(column("select published_at from homeworks where id = ?", published.id())).isEqualTo(NOW);
        assertCode(() -> service.publish(teamId, draft.id(), new PublishHomeworkRequest(true, null, published.updatedAt()), coach),
                ConflictException.class, "error.homework.not_draft");
    }

    @Test
    void aClipMustBeReadyAtPublishButMayStillBeProcessing() {
        for (String status : List.of("PENDING_UPLOAD", "FAILED")) {
            HomeworkDetailResponse draft = service.create(teamId, upsert(List.of(clipItem(clip(coach, status)))), coach);
            assertCode(() -> service.publish(teamId, draft.id(), publishTo(draft, anna), coach),
                    BadRequestException.class, "error.homework.clip_not_ready");
        }
        for (String status : List.of("QUEUED", "PROCESSING", "COMPLETED")) {
            HomeworkDetailResponse draft = service.create(teamId, upsert(List.of(clipItem(clip(coach, status)))), coach);
            assertThat(service.publish(teamId, draft.id(), publishTo(draft, anna), coach).status()).isEqualTo(HomeworkStatus.PUBLISHED);
        }
    }

    @Test
    void aClipItemAddedAfterPublishingMustBeReadyToo() {
        HomeworkDetailResponse published = published(List.of(text("x")));
        List<HomeworkItemRequest> items = new ArrayList<>(echo(published.items()));
        items.add(clipItem(clip(coach, "PENDING_UPLOAD")));

        assertCode(() -> service.update(teamId, published.id(), upsertFor(published, items), coach),
                BadRequestException.class, "error.homework.clip_not_ready");
    }

    // Lifecycle

    @Test
    void aDraftCannotBeArchived() {
        HomeworkDetailResponse draft = service.create(teamId, upsert(List.of(text("x"))), coach);
        assertCode(() -> service.archive(teamId, draft.id(), coach), ConflictException.class, "error.homework.invalid_transition");
    }

    @Test
    void openWorkBlocksTheArchiveUntilItIsDecided() {
        HomeworkDetailResponse published = published(List.of(text("x")));
        UUID assignment = recipientService.listAssignments(teamId, published.id(), coach).get(0).id();

        setAssignment(assignment, "SUBMITTED", 1);
        assertCode(() -> service.archive(teamId, published.id(), coach), ConflictException.class, "error.homework.archive_blocked");

        setAssignment(assignment, "COMPLETED", 1);
        HomeworkDetailResponse archived = service.archive(teamId, published.id(), coach);
        assertThat(archived.status()).isEqualTo(HomeworkStatus.ARCHIVED);
        assertCode(() -> service.archive(teamId, published.id(), coach), ConflictException.class, "error.homework.invalid_transition");
    }

    @Test
    void anArchivedHomeworkIsFinal() {
        HomeworkDetailResponse published = published(List.of(text("x")));
        HomeworkDetailResponse archived = service.archive(teamId, published.id(), coach);

        assertCode(() -> service.update(teamId, published.id(), upsertFor(archived, echo(archived.items())), coach),
                ConflictException.class, "error.homework.closed");
        assertCode(() -> recipientService.addAssignees(teamId, published.id(), new AddHomeworkAssigneesRequest(List.of(bela)), coach),
                ConflictException.class, "error.homework.closed");
        assertCode(() -> recipientService.removeAssignee(teamId, published.id(), anna, coach), ConflictException.class, "error.homework.closed");
    }

    @Test
    void deletingIsPossibleOnlyWithoutHandedInWork() {
        HomeworkDetailResponse published = published(List.of(text("x")));
        UUID assignment = recipientService.listAssignments(teamId, published.id(), coach).get(0).id();
        setAssignment(assignment, "COMPLETED", 1);
        assertCode(() -> service.delete(teamId, published.id(), coach), ConflictException.class, "error.homework.not_draft");

        setAssignment(assignment, "ASSIGNED", 0);
        service.delete(teamId, published.id(), coach);

        assertCode(() -> service.get(teamId, published.id(), coach), NotFoundException.class, "error.homework.not_found");
        assertThat(jdbc.queryForObject("select count(*) from homework_assignments where id = ?", Integer.class, assignment)).isZero();
    }

    // Editing after publishing

    @Test
    void afterAHandInTheAnswerMeaningIsLockedButTheRestStaysEditable() {
        UUID clipId = clip(coach, "COMPLETED");
        UUID otherClip = clip(coach, "COMPLETED");
        HomeworkDetailResponse published = published(List.of(
                text("t"),
                new HomeworkItemRequest(null, "c1", HomeworkItemType.CLIP, "cap", clipId, 5, null, null, null, null, null, null),
                choice(HomeworkQuestionKind.SINGLE_CHOICE, List.of("A", "B"), List.of(0))));
        UUID assignment = recipientService.listAssignments(teamId, published.id(), coach).get(0).id();
        setAssignment(assignment, "SUBMITTED", 1);
        HomeworkItemResponse textItem = published.items().get(0);
        HomeworkItemResponse clipItem = published.items().get(1);
        HomeworkItemResponse questionItem = published.items().get(2);

        assertLocked(published, List.of(edit(textItem, "t"), edit(clipItem, "cap")));
        assertLocked(published, List.of(edit(textItem, "t"), edit(clipItem, "cap"), withQuestion(questionItem, "Más?", List.of("A", "B"), List.of(0), true)));
        assertLocked(published, List.of(edit(textItem, "t"), edit(clipItem, "cap"), withQuestion(questionItem, questionItem.body(), List.of("A", "B"), List.of(1), true)));
        assertLocked(published, List.of(edit(textItem, "t"), edit(clipItem, "cap"), withQuestion(questionItem, questionItem.body(), List.of("A", "C"), List.of(0), true)));
        assertLocked(published, List.of(edit(textItem, "t"), edit(clipItem, "cap"), withQuestion(questionItem, questionItem.body(), List.of("A", "B"), List.of(0), false)));
        assertLocked(published, List.of(edit(textItem, "t"), withClip(clipItem, otherClip, 5), edit(questionItem, questionItem.body())));
        assertLocked(published, List.of(edit(textItem, "t"), withClip(clipItem, clipId, 6), edit(questionItem, questionItem.body())));

        List<HomeworkItemRequest> allowed = new ArrayList<>(List.of(
                edit(questionItem, questionItem.body()),
                edit(clipItem, "new caption"),
                edit(textItem, "new text"),
                text("added")));
        allowed.add(question("Új kötelező?", null));
        HomeworkDetailResponse saved = service.update(teamId, published.id(), new UpsertHomeworkRequest(
                "New title", "New description", NOW.plusDays(3), false, allowed, false, null, published.updatedAt()), coach);

        assertThat(saved.title()).isEqualTo("New title");
        assertThat(saved.description()).isEqualTo("New description");
        assertThat(saved.dueAt()).isEqualTo(NOW.plusDays(3));
        assertThat(saved.allowLate()).isFalse();
        assertThat(saved.items()).extracting(HomeworkItemResponse::body)
                .containsExactly(questionItem.body(), "new caption", "new text", "added", "Új kötelező?");
    }

    @Test
    void aPublishedHomeworkKeepsAnItemAndADeadlineAfterItsPublishTime() {
        HomeworkDetailResponse published = published(List.of(text("x")));

        assertCode(() -> service.update(teamId, published.id(), upsertFor(published, List.of()), coach),
                BadRequestException.class, "validation.homework.items");
        assertCode(() -> service.update(teamId, published.id(), new UpsertHomeworkRequest("T", null, NOW.minusDays(1), null, echo(published.items()), false, null, published.updatedAt()), coach),
                BadRequestException.class, "error.homework.due_before_publish");
    }

    @Test
    void aStaleOrMissingUpdateTimeIsRejected() {
        HomeworkDetailResponse draft = service.create(teamId, upsert(List.of(text("x"))), coach);
        clock.set(NOW.plusMinutes(1));
        // A real change moves the version; a save without changes would keep it.
        HomeworkDetailResponse saved = service.update(teamId, draft.id(), new UpsertHomeworkRequest(
                "Új cím", null, null, null, echo(draft.items()), false, null, draft.updatedAt()), coach);
        assertThat(saved.updatedAt()).isEqualTo(NOW.plusMinutes(1));

        assertCode(() -> service.update(teamId, draft.id(), upsertFor(draft, echo(draft.items())), coach),
                ConflictException.class, "error.homework.stale_write");
        assertCode(() -> service.update(teamId, draft.id(), new UpsertHomeworkRequest("T", null, null, null, echo(draft.items()), false, null, null), coach),
                BadRequestException.class, "validation.homework.expected_updated_at");
    }

    // Assignees

    @Test
    void addingSkipsWhoAlreadyHasARowAndAddedOnesGetTodayAsTheirStart() {
        HomeworkDetailResponse published = published(List.of(text("x")));
        clock.set(NOW.plusHours(1));

        List<AssignmentRowResponse> rows = recipientService.addAssignees(teamId, published.id(), new AddHomeworkAssigneesRequest(List.of(anna, bela)), coach);

        assertThat(rows).hasSize(2);
        assertThat(rows).filteredOn(row -> row.playerProfileId().equals(bela))
                .singleElement().satisfies(row -> assertThat(column("select assigned_at from homework_assignments where id = ?", row.id())).isEqualTo(NOW.plusHours(1)));
        assertThat(rows).filteredOn(row -> row.playerProfileId().equals(anna))
                .singleElement().satisfies(row -> assertThat(column("select assigned_at from homework_assignments where id = ?", row.id())).isEqualTo(NOW));

        UUID stranger = rosterProfile(insertTeam(clubId), "Stranger");
        assertCode(() -> recipientService.addAssignees(teamId, published.id(), new AddHomeworkAssigneesRequest(List.of(stranger)), coach),
                NotFoundException.class, "error.player_profile.not_found");
        assertCode(() -> recipientService.addAssignees(teamId, published.id(), new AddHomeworkAssigneesRequest(List.of()), coach),
                BadRequestException.class, "error.homework.no_assignees");
    }

    @Test
    void addingIsNotPossibleOnADraftOrAnArchivedHomework() {
        HomeworkDetailResponse draft = service.create(teamId, upsert(List.of(text("x"))), coach);
        assertCode(() -> recipientService.addAssignees(teamId, draft.id(), new AddHomeworkAssigneesRequest(List.of(anna)), coach),
                ConflictException.class, "error.homework.invalid_transition");

        HomeworkDetailResponse published = service.publish(teamId, draft.id(), publishTo(draft, anna), coach);
        service.archive(teamId, published.id(), coach);
        assertCode(() -> recipientService.addAssignees(teamId, published.id(), new AddHomeworkAssigneesRequest(List.of(bela)), coach),
                ConflictException.class, "error.homework.closed");
    }

    @Test
    void anAssigneeWhoHandedInCannotBeTakenAway() {
        HomeworkDetailResponse published = published(List.of(text("x")));
        UUID assignment = recipientService.listAssignments(teamId, published.id(), coach).get(0).id();

        setAssignment(assignment, "SUBMITTED", 1);
        assertCode(() -> recipientService.removeAssignee(teamId, published.id(), anna, coach), ConflictException.class, "error.homework.invalid_transition");

        setAssignment(assignment, "ASSIGNED", 0);
        recipientService.removeAssignee(teamId, published.id(), anna, coach);
        assertThat(recipientService.listAssignments(teamId, published.id(), coach)).isEmpty();
        assertCode(() -> recipientService.removeAssignee(teamId, published.id(), anna, coach), NotFoundException.class, "error.homework.assignment_not_found");
    }

    // Copying

    @Test
    void aCopyIsANewDraftWithItsOwnItemsAndReferences() {
        UUID clipId = clip(coach, "COMPLETED");
        HomeworkDetailResponse source = published(List.of(
                new HomeworkItemRequest(null, "c1", HomeworkItemType.CLIP, "cap", clipId, 7, null, null, null, null, null, null),
                timestamp(null, "c1")));

        HomeworkDetailResponse copy = service.duplicate(teamId, source.id(), coach);

        assertThat(copy.id()).isNotEqualTo(source.id());
        assertThat(copy.teamId()).isEqualTo(teamId);
        assertThat(copy.status()).isEqualTo(HomeworkStatus.DRAFT);
        assertThat(column("select published_at from homeworks where id = ?", copy.id())).isNull();
        assertThat(copy.draftRecipientIds()).isEmpty();
        assertThat(copy.draftEntireRoster()).isTrue();
        assertThat(copy.omittedItemCount()).isZero();
        assertThat(recipientService.listAssignments(teamId, copy.id(), coach)).isEmpty();
        assertThat(copy.items()).hasSize(2);
        assertThat(copy.items().get(0).id()).isNotEqualTo(source.items().get(0).id());
        assertThat(copy.items().get(1).refItemId()).isEqualTo(copy.items().get(0).id());
        assertThat(copy.items().get(0).focusMs()).isEqualTo(7);
    }

    @Test
    void aCopyLeavesOutADeletedClipAndTheQuestionPointingToIt() {
        UUID clipId = clip(coach, "COMPLETED");
        HomeworkDetailResponse source = service.create(teamId, upsert(List.of(
                text("keep"),
                new HomeworkItemRequest(null, "c1", HomeworkItemType.CLIP, null, clipId, null, null, null, null, null, null, null),
                timestamp(null, "c1"),
                question("keep too", null))), coach);
        jdbc.update("delete from clips where id = ?", clipId);

        HomeworkDetailResponse copy = service.duplicate(teamId, source.id(), coach);

        assertThat(copy.omittedItemCount()).isEqualTo(2);
        assertThat(copy.items()).extracting(HomeworkItemResponse::body).containsExactly("keep", "keep too");
        assertThat(copy.items()).extracting(HomeworkItemResponse::position).containsExactly(0, 1);
    }

    @Test
    void aCopyStaysInTheTeamAndNeedsWriteThere() {
        UUID clipId = clip(coach, "COMPLETED");
        HomeworkDetailResponse source = service.create(teamId, upsert(List.of(clipItem(clipId))), coach);

        // Another coach of the team copies it.
        User second = coachOn(teamId);
        HomeworkDetailResponse copy = service.duplicate(teamId, source.id(), second);
        assertThat(copy.teamId()).isEqualTo(teamId);
        assertThat(copy.items()).hasSize(1);

        assertCode(() -> service.duplicate(teamId, source.id(), coachOn(insertTeam(clubId))), ForbiddenException.class, "error.auth.forbidden");
        assertCode(() -> service.duplicate(UUID.randomUUID(), source.id(), coach), NotFoundException.class, "error.team.not_found");
    }

    @Test
    void aCoachOfTwoTeamsCannotGiveOneTeamsClipToTheOther() {
        UUID clipId = clip(coach, "COMPLETED");
        UUID otherTeam = insertTeam(clubId);
        User both = coachOn(teamId);
        jdbc.update("insert into coach_teams (coach_id, team_id) values (?, ?)", both.getId(), otherTeam);
        User clubAdmin = clubAdminOf(clubId);

        // They reach the match, but it is not the other team's, so its players could not open it.
        assertCode(() -> service.create(otherTeam, upsert(List.of(clipItem(clipId))), both), ForbiddenException.class, "error.homework.foreign_clip");
        assertCode(() -> service.create(otherTeam, upsert(List.of(clipItem(clipId))), clubAdmin), ForbiddenException.class, "error.homework.foreign_clip");
        assertThat(service.create(teamId, upsert(List.of(clipItem(clipId))), both).items()).hasSize(1);

    }

    @Test
    void aDraftWhoseClipWasDeletedCannotGoOut() {
        UUID clipId = clip(coach, "COMPLETED");
        HomeworkDetailResponse draft = service.create(teamId, upsert(List.of(clipItem(clipId))), coach);
        jdbc.update("delete from clips where id = ?", clipId);

        assertCode(() -> service.publish(teamId, draft.id(), publishTo(draft, anna), coach), BadRequestException.class, "error.homework.clip_missing");
    }

    // Lists

    @Test
    void theProgressBarCountsWhatThePlanSays() {
        HomeworkDetailResponse draft = service.create(teamId, new UpsertHomeworkRequest("T", null, NOW.plusHours(1), null, List.of(text("x")), false, null, null), coach);
        UUID c = rosterProfile(teamId, "C");
        UUID d = rosterProfile(teamId, "D");
        UUID e = rosterProfile(teamId, "E");
        service.publish(teamId, draft.id(), new PublishHomeworkRequest(true, null, draft.updatedAt()), coach);
        List<AssignmentRowResponse> rows = recipientService.listAssignments(teamId, draft.id(), coach);
        setAssignment(rowOf(rows, anna), "ASSIGNED", 0);
        setAssignment(rowOf(rows, bela), "ASSIGNED", 0);
        setAssignment(rowOf(rows, c), "SUBMITTED", 1);
        setAssignment(rowOf(rows, d), "SUBMITTED", 1);
        setAssignment(rowOf(rows, e), "COMPLETED", 1);
        clock.set(NOW.plusHours(2));

        HomeworkSummaryResponse summary = service.list(teamId, "active", null, null, coach).items().stream()
                .filter(item -> item.id().equals(draft.id())).findFirst().orElseThrow();

        assertThat(summary.assignedCount()).isEqualTo(5);
        assertThat(summary.submittedCount()).isEqualTo(3);
        assertThat(summary.completedCount()).isEqualTo(1);
        assertThat(summary.overdueCount()).isEqualTo(2);
        assertThat(summary.toReviewCount()).isEqualTo(2);

        List<AssignmentRowResponse> later = recipientService.listAssignments(teamId, draft.id(), coach);
        assertThat(later).filteredOn(AssignmentRowResponse::overdue).extracting(AssignmentRowResponse::playerProfileId)
                .containsExactlyInAnyOrder(anna, bela);

        jdbc.update("update homework_assignments set status = 'COMPLETED' where homework_id = ? and status = 'SUBMITTED'", draft.id());
        service.archive(teamId, draft.id(), coach);
        HomeworkSummaryResponse archived = service.list(teamId, "archived", null, null, coach).items().stream()
                .filter(item -> item.id().equals(draft.id())).findFirst().orElseThrow();
        assertThat(archived.overdueCount()).isZero();
    }

    @Test
    void eachTabShowsItsOwnAndTheListPages() {
        HomeworkDetailResponse draft = service.create(teamId, upsert(List.of(text("x"))), coach);
        HomeworkDetailResponse active = published(List.of(text("x")));
        HomeworkDetailResponse archived = published(List.of(text("x")));
        service.archive(teamId, archived.id(), coach);

        assertThat(titles("draft")).containsExactly(draft.id());
        assertThat(titles(null)).containsExactly(active.id());
        assertThat(titles("active")).containsExactly(active.id());
        assertThat(titles("archived")).containsExactly(archived.id());
        assertCode(() -> service.list(teamId, "nope", null, null, coach), BadRequestException.class, "error.validation.invalid_request");

        service.create(teamId, upsert(List.of()), coach);
        PageResponse<HomeworkSummaryResponse> page = service.list(teamId, "draft", 2, 1, coach);
        assertThat(page.items()).hasSize(1);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    // Who may do it

    @Test
    void aCoachOfAnotherTeamAndAClubAdminOfAnotherClubGetNothing() {
        HomeworkDetailResponse draft = service.create(teamId, upsert(List.of(text("x"))), coach);
        User otherCoach = coachOn(insertTeam(clubId));
        User otherClubAdmin = clubAdminOf(insertClub());

        for (User stranger : List.of(otherCoach, otherClubAdmin)) {
            assertCode(() -> service.list(teamId, null, null, null, stranger), ForbiddenException.class, "error.auth.forbidden");
            assertCode(() -> service.get(teamId, draft.id(), stranger), ForbiddenException.class, "error.auth.forbidden");
            assertCode(() -> service.get(draft.id(), stranger), ForbiddenException.class, "error.auth.forbidden");
            assertCode(() -> service.create(teamId, upsert(List.of()), stranger), ForbiddenException.class, "error.auth.forbidden");
            assertCode(() -> service.delete(teamId, draft.id(), stranger), ForbiddenException.class, "error.auth.forbidden");
            assertCode(() -> service.publish(teamId, draft.id(), publishTo(draft, anna), stranger), ForbiddenException.class, "error.auth.forbidden");
        }
    }

    @Test
    void aClubAdminWritesOnTheClubsTeamsAndUsesOnlyTheClubsClips() {
        User clubAdmin = clubAdminOf(clubId);
        UUID ownMatchClip = clipOnTeamMatch(teamId, "COMPLETED");
        UUID foreignClip = clipOnTeamMatch(insertTeam(insertClub()), "COMPLETED");

        HomeworkDetailResponse draft = service.create(teamId, upsert(List.of(clipItem(ownMatchClip))), clubAdmin);
        assertThat(service.publish(teamId, draft.id(), publishTo(draft, anna), clubAdmin).status()).isEqualTo(HomeworkStatus.PUBLISHED);

        assertCode(() -> service.create(teamId, upsert(List.of(clipItem(foreignClip))), clubAdmin),
                ForbiddenException.class, "error.homework.foreign_clip");
    }

    @Test
    void existenceComesBeforeAccess() {
        UUID missingTeam = UUID.randomUUID();
        assertCode(() -> service.list(missingTeam, null, null, null, coach), NotFoundException.class, "error.team.not_found");
        assertCode(() -> service.get(UUID.randomUUID(), coach), NotFoundException.class, "error.homework.not_found");

        // A homework of another team is not found through this team, even for its own coach.
        HomeworkDetailResponse mine = service.create(teamId, upsert(List.of(text("x"))), coach);
        UUID otherTeam = insertTeam(clubId);
        jdbc.update("insert into coach_teams (coach_id, team_id) values (?, ?)", coach.getId(), otherTeam);
        assertCode(() -> service.get(otherTeam, mine.id(), coach), NotFoundException.class, "error.homework.not_found");
    }

    @Test
    void theIdOnlyRouteCarriesTheTeam() {
        HomeworkDetailResponse draft = service.create(teamId, upsert(List.of(text("x"))), coach);
        assertThat(service.get(draft.id(), coach).teamId()).isEqualTo(teamId);
    }

    // Request builders

    private static UpsertHomeworkRequest upsert(List<HomeworkItemRequest> items) {
        return new UpsertHomeworkRequest("Nézd meg a 2. gólt", null, null, null, items, false, null, null);
    }

    private static UpsertHomeworkRequest withRecipients(boolean entire, List<UUID> ids) {
        return new UpsertHomeworkRequest("T", null, null, null, List.of(text("x")), entire, ids, null);
    }

    private static UpsertHomeworkRequest upsertFor(HomeworkDetailResponse current, List<HomeworkItemRequest> items) {
        return upsertFor(current, items, false, null);
    }

    private static UpsertHomeworkRequest upsertFor(HomeworkDetailResponse current, List<HomeworkItemRequest> items, boolean entire, List<UUID> ids) {
        return new UpsertHomeworkRequest(current.title(), current.description(), current.dueAt(),
                current.allowLate(), items, entire, ids, current.updatedAt());
    }

    private static PublishHomeworkRequest publishTo(HomeworkDetailResponse draft, UUID... profiles) {
        return new PublishHomeworkRequest(false, List.of(profiles), draft.updatedAt());
    }

    private static HomeworkItemRequest text(String body) {
        return new HomeworkItemRequest(null, null, HomeworkItemType.TEXT, body, null, null, null, null, null, null, null, null);
    }

    private static HomeworkItemRequest question(String body, Boolean required) {
        return new HomeworkItemRequest(null, null, HomeworkItemType.QUESTION, body, null, null, HomeworkQuestionKind.FREE_TEXT, null, null, required, null, null);
    }

    private static HomeworkItemRequest choice(HomeworkQuestionKind kind, List<String> options, List<Integer> correct) {
        return new HomeworkItemRequest(null, null, HomeworkItemType.QUESTION, "Melyik?", null, null, kind, options, correct, null, null, null);
    }

    private static HomeworkItemRequest clipItem(UUID clipId) {
        return new HomeworkItemRequest(null, null, HomeworkItemType.CLIP, null, clipId, null, null, null, null, null, null, null);
    }

    private static HomeworkItemRequest timestamp(UUID refId, String refKey) {
        return new HomeworkItemRequest(null, null, HomeworkItemType.QUESTION, "Hol?", null, null, HomeworkQuestionKind.TIMESTAMP, null, null, null, refId, refKey);
    }

    /** The item as the editor would send it back unchanged, except for its text. */
    private static HomeworkItemRequest edit(HomeworkItemResponse item, String body) {
        return new HomeworkItemRequest(item.id(), null, item.type(), body, item.clipId(), item.focusMs(), item.questionKind(),
                item.options(), item.correctOptions(), item.required(), item.refItemId(), null);
    }

    private static HomeworkItemRequest withQuestion(HomeworkItemResponse item, String body, List<String> options, List<Integer> correct, boolean required) {
        return new HomeworkItemRequest(item.id(), null, item.type(), body, null, null, item.questionKind(), options, correct, required, null, null);
    }

    private static HomeworkItemRequest withClip(HomeworkItemResponse item, UUID clipId, Integer focusMs) {
        return new HomeworkItemRequest(item.id(), null, item.type(), item.body(), clipId, focusMs, null, null, null, null, null, null);
    }

    private static List<HomeworkItemRequest> echo(List<HomeworkItemResponse> items) {
        return items.stream().map(item -> edit(item, item.body())).toList();
    }

    // Fixtures

    private HomeworkDetailResponse published(List<HomeworkItemRequest> items) {
        HomeworkDetailResponse draft = service.create(teamId, upsert(items), coach);
        return service.publish(teamId, draft.id(), publishTo(draft, anna), coach);
    }

    private List<UUID> titles(String tab) {
        return service.list(teamId, tab, null, null, coach).items().stream().map(HomeworkSummaryResponse::id).toList();
    }

    private static UUID rowOf(List<AssignmentRowResponse> rows, UUID profileId) {
        return rows.stream().filter(row -> row.playerProfileId().equals(profileId)).findFirst().orElseThrow().id();
    }

    /** {@code handedIn} sets the hand-in time the way a real hand-in does, so the rules that look at it see one. */
    private void setAssignment(UUID assignmentId, String status, int handedIn) {
        jdbc.update("update homework_assignments set status = ?, submitted_at = case when ? then localtimestamp end where id = ?",
                status, handedIn > 0, assignmentId);
    }

    private LocalDateTime column(String sql, UUID id) {
        return jdbc.queryForObject(sql, LocalDateTime.class, id);
    }

    private UUID insertClub() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into clubs (id, name, created_at) values (?, ?, now())", id, "Club " + id);
        return id;
    }

    private UUID insertTeam(UUID club) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into teams (id, name, club_id) values (?, ?, ?)", id, "Team " + id, club);
        return id;
    }

    private UUID rosterProfile(UUID team, String firstName) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into player_profiles (id, first_name, last_name, created_at) values (?, ?, 'Kovacs', now())", id, firstName);
        jdbc.update("insert into team_roster (player_profile_id, team_id) values (?, ?)", id, team);
        return id;
    }

    private User insertUser(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, email, first_name, last_name, created_at) values (?, ?, 'U', 'V', now())",
                id, id + "@test.com");
        // The role lives only in the token; without one the test states the account's hat here.
        return TestHats.grant(userRepository.findById(id).orElseThrow(), UserRole.valueOf(role));
    }

    private User coachOn(UUID team) {
        User user = insertUser("COACH");
        jdbc.update("insert into coach_teams (coach_id, team_id) values (?, ?)", user.getId(), team);
        return user;
    }

    private User clubAdminOf(UUID club) {
        User user = insertUser("CLUB_ADMIN");
        jdbc.update("insert into club_admins (user_id, club_id) values (?, ?)", user.getId(), club);
        return user;
    }

    /** A clip of the team's own home match, uploaded by this user: the kind a homework may hold. */
    private UUID clip(User uploader, String renderStatus) {
        UUID matchId = UUID.randomUUID();
        jdbc.update("insert into matches (id, uploaded_by_user_id, home_team_id) values (?, ?, ?)", matchId, uploader.getId(), teamId);
        return insertClip(matchId, renderStatus);
    }

    /** A clip of a match without a team, which only its uploader can open. */
    private UUID foreignClip(User uploader, String renderStatus) {
        UUID matchId = UUID.randomUUID();
        jdbc.update("insert into matches (id, uploaded_by_user_id) values (?, ?)", matchId, uploader.getId());
        return insertClip(matchId, renderStatus);
    }

    private UUID clipOnTeamMatch(UUID homeTeam, String renderStatus) {
        UUID matchId = UUID.randomUUID();
        jdbc.update("insert into matches (id, home_team_id) values (?, ?)", matchId, homeTeam);
        return insertClip(matchId, renderStatus);
    }

    private UUID insertClip(UUID matchId, String renderStatus) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into clips (id, match_id, name, created_at, render_status) values (?, ?, 'Clip', now(), ?)", id, matchId, renderStatus);
        return id;
    }

    // Assertions

    private void assertInvalidItem(HomeworkItemRequest item) {
        assertCode(() -> service.create(teamId, upsert(List.of(item)), coach), BadRequestException.class, "error.homework.invalid_item");
    }

    private void assertLocked(HomeworkDetailResponse current, List<HomeworkItemRequest> items) {
        assertCode(() -> service.update(teamId, current.id(), upsertFor(current, items), coach), ConflictException.class, "error.homework.items_locked");
    }

    private static void assertCode(ThrowingCallable call, Class<? extends Throwable> type, String code) {
        assertThatThrownBy(call).isInstanceOf(type).hasFieldOrPropertyWithValue("messageCode", code);
    }
}
