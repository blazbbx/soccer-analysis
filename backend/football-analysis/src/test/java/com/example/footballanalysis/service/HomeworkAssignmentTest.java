package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.HomeworkQuestionKind;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.requests.CommentRequest;
import com.example.footballanalysis.model.requests.HomeworkAnswerRequest;
import com.example.footballanalysis.model.requests.PublishHomeworkRequest;
import com.example.footballanalysis.model.requests.ReturnAssignmentRequest;
import com.example.footballanalysis.model.requests.ReviewAssignmentRequest;
import com.example.footballanalysis.model.db.HomeworkItemType;
import com.example.footballanalysis.model.requests.HomeworkItemRequest;
import com.example.footballanalysis.model.requests.UpsertHomeworkRequest;
import com.example.footballanalysis.model.requests.SubmitAssignmentRequest;
import com.example.footballanalysis.model.responses.AssignmentResponse;
import com.example.footballanalysis.model.responses.HomeworkDetailResponse;
import com.example.footballanalysis.model.responses.HomeworkSummaryCountsResponse;
import com.example.footballanalysis.model.responses.MyHomeworkResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.example.footballanalysis.model.db.HomeworkAssignmentStatus.ASSIGNED;
import static com.example.footballanalysis.model.db.HomeworkAssignmentStatus.COMPLETED;
import static com.example.footballanalysis.model.db.HomeworkAssignmentStatus.RETURNED;
import static com.example.footballanalysis.model.db.HomeworkAssignmentStatus.SUBMITTED;
import static org.assertj.core.api.Assertions.assertThat;

class HomeworkAssignmentTest extends HomeworkPostgresTestBase {

    private UUID anna;
    private User annaUser;

    @BeforeEach
    void fixtures() {
        anna = rosterProfile(teamId, "Anna");
        annaUser = linkedPlayer(anna);
    }

    // Opening

    @Test
    void onlyThePostMarksOpenedAndOnlyTheFirstCounts() {
        UUID id = answerHomework(anna);
        assertThat(assignmentViews.get(id, coach).status()).isEqualTo(ASSIGNED);
        assertThat(assignmentViews.get(id, annaUser).firstViewedAt()).isNull();

        answerService.markViewed(id, annaUser);
        AssignmentResponse first = assignmentViews.get(id, annaUser);
        assertThat(first.status()).isEqualTo(ASSIGNED);
        assertThat(first.firstViewedAt()).isEqualTo(NOW);

        clock.set(NOW.plusHours(1));
        answerService.markViewed(id, annaUser);
        assertThat(assignmentViews.get(id, annaUser).firstViewedAt()).isEqualTo(NOW);
    }

    // Handing in

    @Test
    void anAnswerMustBelongToAQuestionOfThisHomeworkAndStayInRange() {
        HomeworkDetailResponse homework = publishNow(upsert(List.of(
                text("t"),
                choice(HomeworkQuestionKind.SINGLE_CHOICE, List.of("A", "B"), List.of(0)))), anna);
        UUID id = assignmentOf(homework.id(), anna);
        UUID textItem = homework.items().get(0).id();
        UUID choiceItem = homework.items().get(1).id();

        assertCode(() -> handIn(id, annaUser, null, List.of(answer(textItem, "x", null))), BadRequestException.class, "error.homework.invalid_item");
        assertCode(() -> handIn(id, annaUser, null, List.of(answer(UUID.randomUUID(), "x", null))), BadRequestException.class, "error.homework.invalid_item");
        assertCode(() -> handIn(id, annaUser, null, List.of(answer(choiceItem, null, List.of(5)))), BadRequestException.class, "error.homework.invalid_option");
        assertCode(() -> handIn(id, annaUser, null, List.of(answer(choiceItem, null, List.of(0, 1)))), BadRequestException.class, "error.homework.invalid_option");
        assertCode(() -> handIn(id, annaUser, null, List.of(answer(choiceItem, null, List.of(0)), answer(choiceItem, null, List.of(1)))),
                BadRequestException.class, "error.homework.invalid_item");
        // A refused hand-in leaves nothing behind.
        assertThat(jdbc.queryForObject("select count(*) from homework_answers where assignment_id = ?", Integer.class, id)).isZero();

        AssignmentResponse done = handIn(id, annaUser, null, List.of(answer(choiceItem, null, List.of(1))));
        assertThat(done.answers()).singleElement().satisfies(answer -> assertThat(answer.selectedOptions()).containsExactly(1));
    }

    @Test
    void handingInMovesTheCountersAndRecordsWhoDidIt() {
        UUID id = answerHomework(anna);
        answerService.markViewed(id, annaUser);
        clock.set(NOW.plusMinutes(5));

        AssignmentResponse done = handIn(id, annaUser, "válasz", null);

        assertThat(done.status()).isEqualTo(SUBMITTED);
        assertThat(done.responseText()).isEqualTo("válasz");
        assertThat(done.submittedAt()).isEqualTo(NOW.plusMinutes(5));
        assertThat(done.submittedByParent()).isFalse();
        assertThat(jdbc.queryForObject(
                "select details from audit_events where event_type = 'HOMEWORK_SUBMITTED' and target_id = ?", String.class, id.toString()))
                .contains("submittedByRole=PLAYER").doesNotContain("válasz");
    }

    @Test
    void aParentMayHandInEvenWhenTheChildHasALoginAndItIsRecorded() {
        User parent = parentOf(anna);
        UUID id = answerHomework(anna);
        answerService.markViewed(id, parent);

        AssignmentResponse done = handIn(id, parent, "szülő írta", null);

        assertThat(done.submittedByParent()).isTrue();
        assertThat(done.submittedByFirstName()).isEqualTo("U");
        assertThat(done.submittedByLastName()).isEqualTo("V");
        assertThat(jdbc.queryForObject(
                "select details from audit_events where event_type = 'HOMEWORK_SUBMITTED' and target_id = ?", String.class, id.toString()))
                .contains("submittedByRole=PARENT");
    }

    @Test
    void aPageShowingOldQuestionsCannotHandIn() {
        HomeworkDetailResponse homework = publishNow(upsert(List.of(question("Miért?", true))), anna);
        UUID id = assignmentOf(homework.id(), anna);
        UUID item = homework.items().get(0).id();
        LocalDateTime loaded = homeworkUpdatedAt(id);
        // The page gets this time with the assignment and sends it back with the hand-in.
        assertThat(assignmentViews.get(id, annaUser).homeworkUpdatedAt()).isEqualTo(loaded);
        assertCode(() -> answerService.submit(id, new SubmitAssignmentRequest("x", null, null), annaUser),
                BadRequestException.class, "validation.homework.expected_updated_at");

        clock.set(NOW.plusMinutes(1));
        homeworkService.update(teamId, homework.id(), new UpsertHomeworkRequest("Nézd meg a 2. gólt", null, null, null,
                List.of(new HomeworkItemRequest(item, null, HomeworkItemType.QUESTION, "Miért pont ott?", null, null,
                        HomeworkQuestionKind.FREE_TEXT, null, null, true, null, null)),
                false, null, homework.updatedAt()), coach);

        assertCode(() -> answerService.submit(id, new SubmitAssignmentRequest(null, List.of(answer(item, "mert", null)), loaded), annaUser),
                ConflictException.class, "error.homework.stale_write");
        assertThat(assignmentViews.get(id, annaUser).status()).isEqualTo(ASSIGNED);
        assertThat(handIn(id, annaUser, null, List.of(answer(item, "mert", null))).status()).isEqualTo(SUBMITTED);
    }

    @Test
    void requiredQuestionsAndEmptyHandInsAreRejected() {
        HomeworkDetailResponse homework = publishNow(upsert(List.of(question("Kötelező", true), question("Nem kötelező", false))), anna);
        UUID id = assignmentOf(homework.id(), anna);
        UUID required = homework.items().get(0).id();
        UUID optional = homework.items().get(1).id();

        assertCode(() -> handIn(id, annaUser, null, null), BadRequestException.class, "error.homework.required_answer_missing");
        assertCode(() -> handIn(id, annaUser, null, List.of(answer(optional, "csak ez", null))),
                BadRequestException.class, "error.homework.required_answer_missing");
        assertCode(() -> handIn(id, annaUser, null, List.of(answer(required, " ", null))),
                BadRequestException.class, "error.homework.required_answer_missing");
        assertThat(handIn(id, annaUser, null, List.of(answer(required, "megvan", null))).status()).isEqualTo(SUBMITTED);
    }

    @Test
    void aHomeworkWithOnlyOptionalQuestionsStillNeedsSomething() {
        HomeworkDetailResponse homework = publishNow(upsert(List.of(question("Nem kötelező", false))), anna);
        UUID id = assignmentOf(homework.id(), anna);

        assertCode(() -> handIn(id, annaUser, " ", null), BadRequestException.class, "error.homework.required_answer_missing");
        assertThat(handIn(id, annaUser, "általános válasz", null).status()).isEqualTo(SUBMITTED);
    }

    @Test
    void aHomeworkWithoutQuestionsMayBeHandedInEmpty() {
        UUID id = answerHomework(anna);

        AssignmentResponse done = handIn(id, annaUser, null, null);

        assertThat(done.status()).isEqualTo(SUBMITTED);
        assertThat(done.responseText()).isNull();
    }

    @Test
    void aRequiredTimestampQuestionOfADeletedClipDoesNotBlock() {
        UUID clipId = clip(coach, "COMPLETED");
        HomeworkDetailResponse homework = publishNow(upsert(List.of(keyedClip("c1", clipId), timestamp("c1", true))), anna);
        UUID id = assignmentOf(homework.id(), anna);
        assertCode(() -> handIn(id, annaUser, "általános", null), BadRequestException.class, "error.homework.required_answer_missing");

        jdbc.update("delete from clips where id = ?", clipId);

        assertThat(handIn(id, annaUser, "általános", null).status()).isEqualTo(SUBMITTED);
    }

    @Test
    void aSubmittedOneCannotBeHandedInAgain() {
        UUID id = answerHomework(anna);
        handIn(id, annaUser, "a", null);

        assertCode(() -> handIn(id, annaUser, "b", null), ConflictException.class, "error.homework.invalid_transition");
        assertThat(assignmentViews.get(id, annaUser).responseText()).isEqualTo("a");
    }

    @Test
    void theStaffSeesTheWorkOnceItIsHandedIn() {
        HomeworkDetailResponse homework = publishNow(upsert(List.of(question("Mi történt?", null))), anna);
        UUID id = assignmentOf(homework.id(), anna);
        UUID item = homework.items().get(0).id();
        User clubAdmin = clubAdminOf(clubId);
        answerService.markViewed(id, annaUser);
        assertThat(assignmentViews.get(id, coach).firstViewedAt()).isEqualTo(NOW);

        handIn(id, annaUser, "általános", List.of(answer(item, "ez", null)));

        for (User staff : List.of(coach, clubAdmin)) {
            AssignmentResponse seen = assignmentViews.get(id, staff);
            assertThat(seen.responseText()).isEqualTo("általános");
            assertThat(seen.answers()).hasSize(1);
        }
    }

    // Deadlines

    @Test
    void lateWorkIsRefusedWhenNotAllowed() {
        HomeworkDetailResponse homework = publishNow(upsert(NOW.plusHours(1), false, List.of(text("x"))), anna);
        UUID id = assignmentOf(homework.id(), anna);
        answerService.markViewed(id, annaUser);
        clock.set(NOW.plusHours(2));

        assertCode(() -> handIn(id, annaUser, "a", null), ConflictException.class, "error.homework.closed");
        assertThat(assignmentViews.get(id, annaUser).lateBlocked()).isTrue();
        assertThat(assignmentViews.get(id, annaUser).writable()).isFalse();
    }

    @Test
    void lateIsMarkedWhenAllowed() {
        HomeworkDetailResponse homework = publishNow(upsert(NOW.plusHours(1), null, List.of(text("x"))), anna);
        UUID id = assignmentOf(homework.id(), anna);
        answerService.markViewed(id, annaUser);
        clock.set(NOW.plusHours(2));
        assertThat(assignmentViews.get(id, annaUser).overdue()).isTrue();

        assertThat(handIn(id, annaUser, "késve", null).late()).isTrue();
    }

    @Test
    void anArchivedHomeworkRefusesDecisions() {
        UUID id = answerHomework(anna);
        UUID homeworkId = jdbc.queryForObject("select homework_id from homework_assignments where id = ?", UUID.class, id);
        homeworkService.archive(teamId, homeworkId, coach);

        assertCode(() -> review(id, coach), ConflictException.class, "error.homework.closed");
        assertCode(() -> commentService.addComment(id, new CommentRequest("x"), coach),
                ConflictException.class, "error.homework.closed");
    }

    // Review

    @Test
    void reviewAcceptsOnlySubmittedWorkAndOnlyOnce() {
        UUID open = answerHomework(anna);
        assertCode(() -> review(open, coach), ConflictException.class, "error.homework.invalid_transition");

        UUID id = submitted(anna);
        AssignmentResponse accepted = reviewService.review(id, new ReviewAssignmentRequest("Szép"), coach);
        assertThat(accepted.status()).isEqualTo(COMPLETED);
        assertThat(accepted.coachFeedback()).isEqualTo("Szép");
        assertThat(accepted.reviewedAt()).isEqualTo(NOW);

        assertCode(() -> review(id, coach), ConflictException.class, "error.homework.invalid_transition");
        assertThat(jdbc.queryForObject("select count(*) from audit_events where event_type = 'HOMEWORK_REVIEWED' and target_id = ?", Integer.class, id.toString()))
                .isEqualTo(1);
    }

    @Test
    void theSecondCoachSeesTheFirstDecisionInsteadOfOverwritingIt() {
        UUID id = submitted(anna);
        User second = coachOn(teamId);
        review(id, coach);

        assertCode(() -> review(id, second), ConflictException.class, "error.homework.invalid_transition");
        assertThat(assignmentViews.get(id, coach).status()).isEqualTo(COMPLETED);
    }

    // Sending back

    @Test
    void onlySubmittedWorkCanBeSentBackAndTheNoteReachesThePlayer() {
        UUID open = answerHomework(anna);
        assertCode(() -> sendBack(open, coach, "x"), ConflictException.class, "error.homework.invalid_transition");

        UUID id = submitted(anna);
        clock.set(NOW.plusMinutes(10));
        AssignmentResponse returned = sendBack(id, coach, "  A 2. kérdés hiányos.  ");

        assertThat(returned.status()).isEqualTo(RETURNED);
        assertThat(returned.returnNote()).isEqualTo("A 2. kérdés hiányos.");
        assertThat(returned.returnedAt()).isEqualTo(NOW.plusMinutes(10));
        assertThat(returned.reviewedAt()).isNull();
        assertThat(returned.responseText()).isEqualTo("válasz");
        assertThat(commentService.listComments(id, annaUser)).singleElement()
                .satisfies(comment -> assertThat(comment.body()).isEqualTo("A 2. kérdés hiányos."));
        assertThat(cards("todo", anna, annaUser)).extracting(card -> card.assignmentId()).contains(id);
        assertThat(assignmentViews.get(id, annaUser).writable()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from audit_events where event_type = 'HOMEWORK_RETURNED' and target_id = ?",
                Integer.class, id.toString())).isEqualTo(1);

        // Sent back is not submitted: neither a second decision nor the to-review count sees it.
        assertCode(() -> sendBack(id, coach, "x"), ConflictException.class, "error.homework.invalid_transition");
        assertCode(() -> review(id, coach), ConflictException.class, "error.homework.invalid_transition");
        assertThat(assignmentViews.summary("COACH", coach).toReview()).isZero();
        assertThat(assignmentViews.summary("PLAYER", annaUser).open()).isEqualTo(2);
    }

    @Test
    void aFixReplacesTheEarlierHandInAndCanThenBeAccepted() {
        HomeworkDetailResponse homework = publishNow(upsert(List.of(question("Első", false), question("Második", false))), anna);
        UUID id = assignmentOf(homework.id(), anna);
        UUID first = homework.items().get(0).id();
        UUID second = homework.items().get(1).id();
        handIn(id, annaUser, "régi", List.of(answer(first, "a", null), answer(second, "b", null)));
        sendBack(id, coach, "Írd át az elsőt.");

        clock.set(NOW.plusHours(1));
        AssignmentResponse fixed = handIn(id, annaUser, null, List.of(answer(first, "jobb", null)));

        assertThat(fixed.status()).isEqualTo(SUBMITTED);
        assertThat(fixed.submittedAt()).isEqualTo(NOW.plusHours(1));
        assertThat(fixed.responseText()).isNull();
        assertThat(fixed.answers()).singleElement().satisfies(answer -> {
            assertThat(answer.itemId()).isEqualTo(first);
            assertThat(answer.textAnswer()).isEqualTo("jobb");
        });
        assertThat(fixed.returnNote()).isEqualTo("Írd át az elsőt.");
        assertThat(jdbc.queryForList("select details from audit_events where event_type = 'HOMEWORK_SUBMITTED' and target_id = ?",
                String.class, id.toString()))
                .hasSize(2)
                .anySatisfy(details -> assertThat(details).contains("resubmission=false"))
                .anySatisfy(details -> assertThat(details).contains("resubmission=true"));
        assertThat(review(id, coach).status()).isEqualTo(COMPLETED);
    }

    @Test
    void theDeadlineDoesNotCloseWorkSentBackAfterIt() {
        HomeworkDetailResponse homework = publishNow(upsert(NOW.plusHours(1), false, List.of(text("x"))), anna);
        UUID id = assignmentOf(homework.id(), anna);
        handIn(id, annaUser, "időben", null);
        clock.set(NOW.plusHours(2));
        sendBack(id, coach, "Még egyszer.");

        AssignmentResponse returned = assignmentViews.get(id, annaUser);
        assertThat(returned.overdue()).isFalse();
        assertThat(returned.lateBlocked()).isFalse();
        assertThat(returned.writable()).isTrue();
        assertThat(assignmentViews.summary("PLAYER", annaUser).overdue()).isZero();

        clock.set(NOW.plusHours(3));
        AssignmentResponse fixed = handIn(id, annaUser, "javítva", null);
        assertThat(fixed.status()).isEqualTo(SUBMITTED);
        assertThat(fixed.late()).isFalse();
    }

    @Test
    void onlyTheTeamsStaffCanSendBack() {
        UUID id = submitted(anna);
        User otherCoach = coachOn(insertTeam(clubId));
        User parent = parentOf(anna);

        for (User stranger : List.of(annaUser, parent, otherCoach, clubAdminOf(insertClub()))) {
            assertCode(() -> sendBack(id, stranger, "x"), ForbiddenException.class, "error.auth.forbidden");
        }
        assertThat(sendBack(id, clubAdminOf(clubId), "Nézd át.").status()).isEqualTo(RETURNED);
    }

    @Test
    void returnedWorkCanBeArchivedAndThenItIsClosed() {
        UUID id = submitted(anna);
        sendBack(id, coach, "Javítsd.");
        UUID homeworkId = jdbc.queryForObject("select homework_id from homework_assignments where id = ?", UUID.class, id);

        homeworkService.archive(teamId, homeworkId, coach);

        assertThat(assignmentViews.get(id, annaUser).writable()).isFalse();
        assertCode(() -> handIn(id, annaUser, "késő", null), ConflictException.class, "error.homework.closed");
        assertThat(cards("done", anna, annaUser)).singleElement()
                .satisfies(card -> {
                    assertThat(card.homeworkStatus()).isEqualTo(com.example.footballanalysis.model.db.HomeworkStatus.ARCHIVED);
                    assertThat(card.status()).isEqualTo(RETURNED);
                });
    }

    // Tabs

    @Test
    void everyStatusAndHomeworkStateIsOnExactlyOneTab() {
        record Case(String status, int handedIn, String homework, String tab) {}
        List<Case> cases = List.of(
                new Case("ASSIGNED", 0, "PUBLISHED", "todo"),
                new Case("SUBMITTED", 1, "PUBLISHED", "submitted"),
                new Case("RETURNED", 1, "PUBLISHED", "todo"),
                new Case("RETURNED", 1, "ARCHIVED", "done"),
                new Case("COMPLETED", 1, "PUBLISHED", "done"),
                new Case("SUBMITTED", 1, "ARCHIVED", "submitted"),
                new Case("ASSIGNED", 0, "ARCHIVED", "done"));
        for (Case each : cases) {
            UUID profile = rosterProfile(teamId, "P");
            User player = linkedPlayer(profile);
            UUID id = answerHomework(profile);
            setStatus(id, each.status(), each.handedIn());
            jdbc.update("update homeworks set status = ? where id = (select homework_id from homework_assignments where id = ?)", each.homework(), id);
            for (String tab : List.of("todo", "submitted", "done")) {
                assertThat(cards(tab, profile, player)).as(each + " on " + tab).hasSize(tab.equals(each.tab()) ? 1 : 0);
                assertThat(assignmentViews.listMine(tab, profile, 1, 20, player).total()).as(each + " total " + tab)
                        .isEqualTo(tab.equals(each.tab()) ? 1 : 0);
            }
        }
    }

    @Test
    void theTodoTabPutsTheNearestDeadlineFirstAndLabelsTheCards() {
        publishNow(upsert(null, null, List.of(text("no deadline"))), anna);
        HomeworkDetailResponse later = publishNow(upsert(NOW.plusDays(5), null, List.of(text("later"))), anna);
        HomeworkDetailResponse soon = publishNow(upsert(NOW.plusHours(20), null, List.of(text("soon"))), anna);
        HomeworkDetailResponse past = publishNow(upsert(NOW.plusHours(1), false, List.of(text("past"))), anna);
        clock.set(NOW.plusHours(2));

        List<MyHomeworkResponse> cards = cards("todo", anna, annaUser);

        assertThat(cards).extracting(MyHomeworkResponse::homeworkId).startsWith(past.id(), soon.id(), later.id());
        assertThat(cards).hasSize(4);
        assertThat(cards.get(0).overdue()).isTrue();
        assertThat(cards.get(0).lateBlocked()).isTrue();
        assertThat(cards.get(1).dueSoon()).isTrue();
        assertThat(cards.get(0).teamName()).startsWith("Team ");
    }

    @Test
    void theListNeedsAProfileTheCallerActsFor() {
        User stranger = insertUser("PARENT");
        User parentWithoutChoice = parentOf(anna);
        assertCode(() -> assignmentViews.listMine("todo", anna, null, null, stranger), ForbiddenException.class, "error.auth.forbidden");
        assertCode(() -> assignmentViews.listMine("todo", null, null, null, parentWithoutChoice),
                BadRequestException.class, "validation.homework.profile_required");
        assertCode(() -> assignmentViews.listMine("nope", anna, null, null, annaUser), BadRequestException.class, "error.validation.invalid_request");
        assertCode(() -> assignmentViews.listMine("todo", UUID.randomUUID(), null, null, annaUser),
                NotFoundException.class, "error.player_profile.not_found");
        User noProfile = insertUser("PLAYER");
        assertCode(() -> assignmentViews.listMine("todo", null, null, null, noProfile),
                BadRequestException.class, "validation.homework.profile_required");

        answerHomework(anna);
        assertThat(assignmentViews.listMine(null, null, null, null, annaUser).items()).hasSize(1);
        assertThat(assignmentViews.listMine("todo", anna, null, null, parentWithoutChoice).items()).hasSize(1);
    }

    // Summary

    @Test
    void theSummaryCountsTheTodoTabOfThePlayer() {
        publishNow(upsert(NOW.plusHours(1), null, List.of(text("x"))), anna);
        publishNow(upsert(NOW.plusDays(2), null, List.of(text("x"))), anna);
        UUID submitted = answerHomework(anna);
        setStatus(submitted, "SUBMITTED", 1);
        clock.set(NOW.plusHours(2));

        HomeworkSummaryCountsResponse summary = assignmentViews.summary("PLAYER", annaUser);

        assertThat(summary.open()).isEqualTo(2);
        assertThat(summary.overdue()).isEqualTo(1);
        assertThat(summary.nextDueAt()).isEqualTo(NOW.plusDays(2));
        assertThat(summary.toReview()).isZero();
    }

    @Test
    void aParentsSummaryAddsUpEveryChild() {
        UUID bela = rosterProfile(teamId, "Bela");
        User parent = parentOf(anna, bela);
        answerHomework(anna);
        answerHomework(bela);
        answerHomework(bela);

        assertThat(assignmentViews.summary("PARENT", parent).open()).isEqualTo(3);
    }

    @Test
    void theToReviewCountFollowsTheScopeOfTheView() {
        UUID id = submitted(anna);
        UUID otherClubTeam = insertTeam(insertClub());
        User otherCoach = coachOn(otherClubTeam);

        assertThat(assignmentViews.summary("COACH", coach).toReview()).isEqualTo(1);
        assertThat(assignmentViews.summary("COACH", otherCoach).toReview()).isZero();
        assertThat(assignmentViews.summary("CLUB_ADMIN", clubAdminOf(clubId)).toReview()).isEqualTo(1);
        assertThat(assignmentViews.summary("CLUB_ADMIN", clubAdminOf(insertClub())).toReview()).isZero();
        // The platform admin reviews no homework, so the sidebar shows nothing for that view.
        assertThat(assignmentViews.summary("ADMIN", insertUser("ADMIN"))).isEqualTo(HomeworkSummaryCountsResponse.empty());
        review(id, coach);
        assertThat(assignmentViews.summary("COACH", coach).toReview()).isZero();
    }

    @Test
    void aViewWithoutItsHatGivesOnlyZeros() {
        submitted(anna);
        HomeworkSummaryCountsResponse zeros = HomeworkSummaryCountsResponse.empty();

        assertThat(assignmentViews.summary("COACH", annaUser)).isEqualTo(zeros);
        assertThat(assignmentViews.summary("ADMIN", coach)).isEqualTo(zeros);
        assertThat(assignmentViews.summary("NOPE", coach)).isEqualTo(zeros);
    }

    // Who sees what

    @Test
    void strangersGetNothingAndManagersSeeWithoutActingAsRecipients() {
        UUID id = answerHomework(anna);
        User otherPlayer = linkedPlayer(rosterProfile(teamId, "Bela"));
        User otherParent = parentOf(rosterProfile(teamId, "Cili"));
        User otherCoach = coachOn(insertTeam(clubId));
        User otherClubAdmin = clubAdminOf(insertClub());

        for (User stranger : List.of(otherPlayer, otherParent, otherCoach, otherClubAdmin)) {
            assertCode(() -> assignmentViews.get(id, stranger), ForbiddenException.class, "error.auth.forbidden");
            assertCode(() -> answerService.markViewed(id, stranger), ForbiddenException.class, "error.auth.forbidden");
            assertCode(() -> handIn(id, stranger, "x", null), ForbiddenException.class, "error.auth.forbidden");
            assertCode(() -> commentService.listComments(id, stranger), ForbiddenException.class, "error.auth.forbidden");
            assertCode(() -> commentService.addComment(id, new CommentRequest("x"), stranger),
                    ForbiddenException.class, "error.auth.forbidden");
        }
        for (User stranger : List.of(otherCoach, otherClubAdmin, annaUser)) {
            assertCode(() -> review(id, stranger), ForbiddenException.class, "error.auth.forbidden");
        }
        assertThat(assignmentViews.get(id, clubAdminOf(clubId)).id()).isEqualTo(id);
    }

    @Test
    void theCorrectOptionsReachTheRecipientOnlyAfterAcceptance() {
        HomeworkDetailResponse homework = publishNow(upsert(List.of(
                choice(HomeworkQuestionKind.SINGLE_CHOICE, List.of("A", "B"), List.of(1)))), anna);
        UUID id = assignmentOf(homework.id(), anna);
        UUID item = homework.items().get(0).id();
        answerService.markViewed(id, annaUser);

        assertThat(assignmentViews.get(id, annaUser).items().get(0).correctOptions()).isNull();
        assertThat(assignmentViews.get(id, coach).items().get(0).correctOptions()).containsExactly(1);

        assertThat(handIn(id, annaUser, null, List.of(answer(item, null, List.of(0)))).items().get(0).correctOptions()).isNull();
        review(id, coach);
        assertThat(assignmentViews.get(id, annaUser).items().get(0).correctOptions()).containsExactly(1);
    }

    // Comments

    @Test
    void theThreadIsOrderedAndTrimmed() {
        UUID id = answerHomework(anna);

        commentService.addComment(id, new CommentRequest("Melyik játékost?"), annaUser);
        clock.set(NOW.plusMinutes(1));
        commentService.addComment(id, new CommentRequest("  Nézd meg a második félidőt  "), coach);

        assertThat(commentService.listComments(id, annaUser)).hasSize(2).satisfies(comments -> {
            assertThat(comments.get(0).body()).isEqualTo("Melyik játékost?");
            assertThat(comments.get(1).body()).isEqualTo("Nézd meg a második félidőt");
            assertThat(comments.get(1).mine()).isFalse();
        });
    }

    @Test
    void theConversationGoesWithTheRemovedPlayersWork() {
        UUID id = submitted(anna);
        commentService.addComment(id, new CommentRequest("rendben"), annaUser);
        recipientService.releaseForRosterRemoval(anna, teamId);
        leaveRoster(anna);

        for (User writer : List.of(annaUser, coach)) {
            assertCode(() -> commentService.addComment(id, new CommentRequest("x"), writer),
                    NotFoundException.class, "error.homework.assignment_not_found");
        }
        assertThat(jdbc.queryForObject("select count(*) from homework_comments where assignment_id = ?", Integer.class, id)).isZero();
    }

    // Roster removal

    @Test
    void removalDeletesAllOfThePlayersWorkOnTheTeam() {
        UUID opened = answerHomework(anna);
        answerService.markViewed(opened, annaUser);
        UUID accepted = submitted(anna);
        review(accepted, coach);
        UUID waiting = submitted(anna);
        HomeworkDetailResponse draft = homeworkService.create(teamId, new com.example.footballanalysis.model.requests.UpsertHomeworkRequest(
                "T", null, null, null, List.of(), false, List.of(anna), null), coach);
        clock.set(NOW.plusDays(1));

        recipientService.releaseForRosterRemoval(anna, teamId);
        leaveRoster(anna);

        for (UUID id : List.of(opened, accepted, waiting)) {
            assertThat(jdbc.queryForObject("select count(*) from homework_assignments where id = ?", Integer.class, id)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from homework_answers where assignment_id = ?", Integer.class, id)).isZero();
        }
        assertThat(homeworkService.get(teamId, draft.id(), coach).draftRecipientIds()).isEmpty();
        assertCode(() -> review(waiting, coach), NotFoundException.class, "error.homework.assignment_not_found");
    }

    @Test
    void aReturningPlayerStartsAfresh() {
        UUID before = submitted(anna);
        UUID homeworkId = jdbc.queryForObject("select homework_id from homework_assignments where id = ?", UUID.class, before);
        recipientService.releaseForRosterRemoval(anna, teamId);
        leaveRoster(anna);
        joinRoster(anna, teamId);

        assertThat(recipientService.addAssignees(teamId, homeworkId,
                new com.example.footballanalysis.model.requests.AddHomeworkAssigneesRequest(List.of(anna)), coach)).singleElement()
                .satisfies(row -> {
                    assertThat(row.id()).isNotEqualTo(before);
                    assertThat(row.status()).isEqualTo(ASSIGNED);
                    assertThat(row.submittedAt()).isNull();
                });
    }

    // The clip

    @Test
    void aHomeworkAddsNoClipGrantSoAForeignClipStaysClosed() {
        // An item written before the team-clip rule, straight into the table.
        UUID foreign = foreignClip(coach, "COMPLETED");
        HomeworkDetailResponse homework = publishNow(upsert(List.of(text("x"))), anna);
        jdbc.update("insert into homework_items (id, homework_id, position, type, clip_id, required) values (?, ?, 1, 'CLIP', ?, false)",
                UUID.randomUUID(), homework.id(), foreign);

        assertThat(userAccessService.canAccessClip(annaUser, foreign)).isFalse();
        assertThat(userAccessService.canAccessClip(parentOf(anna), foreign)).isFalse();
        assertThat(userAccessService.canAccessClip(coachOn(teamId), foreign)).isFalse();
    }

    @Test
    void theTeamsOwnClipOpensToItsPlayersParentsAndStaffWithoutAHomework() {
        UUID clipId = clip(coach, "COMPLETED");

        assertThat(userAccessService.canAccessClip(annaUser, clipId)).isTrue();
        assertThat(userAccessService.canAccessClip(parentOf(anna), clipId)).isTrue();
        assertThat(userAccessService.canAccessClip(coachOn(teamId), clipId)).isTrue();
        assertThat(userAccessService.canAccessClip(clubAdminOf(clubId), clipId)).isTrue();
        assertThat(userAccessService.canAccessClip(coachOn(insertTeam(clubId)), clipId)).isFalse();
        assertThat(userAccessService.canManageClip(annaUser, clipId)).isFalse();
    }

    // Leaving the team

    @Test
    void aRemovedPlayerNoLongerReachesTheirWork() {
        User parent = parentOf(anna);
        UUID id = answerHomework(anna);
        handIn(id, annaUser, "kész", null);
        UUID done = answerHomework(anna);
        handIn(done, annaUser, "kész", null);
        review(done, coach);

        recipientService.releaseForRosterRemoval(anna, teamId);
        leaveRoster(anna);

        for (User caller : List.of(annaUser, parent, coach)) {
            for (UUID gone : List.of(id, done)) {
                assertCode(() -> assignmentViews.get(gone, caller), NotFoundException.class, "error.homework.assignment_not_found");
                assertCode(() -> answerService.markViewed(gone, caller), NotFoundException.class, "error.homework.assignment_not_found");
                assertCode(() -> commentService.listComments(gone, caller), NotFoundException.class, "error.homework.assignment_not_found");
            }
        }
    }

    // Recipients and the deadline

    @Test
    void noPlayerCanBeAddedPastADeadlineThatTakesNoLateWork() {
        UUID bela = rosterProfile(teamId, "Bela");
        HomeworkDetailResponse homework = publishNow(upsert(NOW.plusHours(1), false, List.of(text("x"))), anna);
        clock.set(NOW.plusHours(2));

        assertCode(() -> recipientService.addAssignees(teamId, homework.id(),
                        new com.example.footballanalysis.model.requests.AddHomeworkAssigneesRequest(List.of(bela)), coach),
                ConflictException.class, "error.homework.assign_after_deadline");
    }

    @Test
    void aPlayerCanBeAddedPastADeadlineThatTakesLateWork() {
        UUID bela = rosterProfile(teamId, "Bela");
        HomeworkDetailResponse homework = publishNow(upsert(NOW.plusHours(1), true, List.of(text("x"))), anna);
        clock.set(NOW.plusHours(2));

        assertThat(recipientService.addAssignees(teamId, homework.id(),
                new com.example.footballanalysis.model.requests.AddHomeworkAssigneesRequest(List.of(bela)), coach)).hasSize(2);
    }

    // Saving without changes

    @Test
    void aSaveWithoutChangesKeepsTheVersionSoAnOpenHandInStillGoesThrough() {
        HomeworkDetailResponse homework = publishNow(upsert(List.of(question("Miért?", false))), anna);
        UUID id = assignmentOf(homework.id(), anna);
        LocalDateTime loaded = homeworkUpdatedAt(id);
        HomeworkDetailResponse current = homeworkService.get(teamId, homework.id(), coach);

        clock.set(NOW.plusMinutes(5));
        HomeworkDetailResponse saved = homeworkService.update(teamId, homework.id(), new UpsertHomeworkRequest(
                current.title(), current.description(), current.dueAt(), current.allowLate(),
                current.items().stream().map(item -> new HomeworkItemRequest(item.id(), null, item.type(), item.body(), item.clipId(),
                        item.focusMs(), item.questionKind(), item.options(), item.correctOptions(), item.required(), item.refItemId(), null)).toList(),
                false, null, current.updatedAt()), coach);

        assertThat(saved.updatedAt()).isEqualTo(current.updatedAt());
        assertThat(homeworkUpdatedAt(id)).isEqualTo(loaded);
        assertThat(handIn(id, annaUser, "kész", null).status()).isEqualTo(SUBMITTED);
    }

    // Helpers

    private UUID answerHomework(UUID profileId) {
        HomeworkDetailResponse homework = publishNow(upsert(List.of(text("x"))), profileId);
        return assignmentOf(homework.id(), profileId);
    }

    private UUID submitted(UUID profileId) {
        UUID id = answerHomework(profileId);
        answerService.markViewed(id, annaUser);
        handIn(id, annaUser, "válasz", null);
        return id;
    }

    private static HomeworkAnswerRequest answer(UUID itemId, String text, List<Integer> selected) {
        return new HomeworkAnswerRequest(itemId, text, selected, null);
    }

    private AssignmentResponse review(UUID id, User actor) {
        return reviewService.review(id, new ReviewAssignmentRequest(null), actor);
    }

    private AssignmentResponse sendBack(UUID id, User actor, String note) {
        return reviewService.returnForRevision(id, new ReturnAssignmentRequest(note), actor);
    }

    private List<MyHomeworkResponse> cards(String tab, UUID profileId, User actor) {
        return assignmentViews.listMine(tab, profileId, 1, 50, actor).items();
    }
}
