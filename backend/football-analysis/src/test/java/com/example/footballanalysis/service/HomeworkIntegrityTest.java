package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.model.db.HomeworkQuestionKind;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.requests.HomeworkAnswerRequest;
import com.example.footballanalysis.model.responses.AssignmentResponse;
import com.example.footballanalysis.model.requests.ReviewAssignmentRequest;
import com.example.footballanalysis.model.responses.HomeworkDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static com.example.footballanalysis.model.db.HomeworkAssignmentStatus.COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;

/** The rules found in review: what a stale copy, a changed question or a second hat must not be able to do. */
class HomeworkIntegrityTest extends HomeworkPostgresTestBase {

    private UUID anna;
    private User annaUser;

    @BeforeEach
    void fixtures() {
        anna = rosterProfile(teamId, "Anna");
        annaUser = linkedPlayer(anna);
    }

    @Test
    void aParentWhoIsAlsoTheCoachDoesNotSeeTheKeyWhileAnswering() {
        HomeworkDetailResponse homework = publishNow(upsert(List.of(
                choice(HomeworkQuestionKind.SINGLE_CHOICE, List.of("A", "B"), List.of(1)))), anna);
        UUID id = assignmentOf(homework.id(), anna);
        jdbc.update("insert into player_parents (user_id, player_profile_id, created_at) values (?, ?, now())", coach.getId(), anna);
        User otherCoach = coachOn(teamId);

        // Both hats come from the token, as in a real request.
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                coach.getId().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_COACH"), new SimpleGrantedAuthority("ROLE_PARENT"))));
        try {
            assertThat(assignmentViews.get(id, coach).items().get(0).correctOptions()).isNull();
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertThat(assignmentViews.get(id, otherCoach).items().get(0).correctOptions()).containsExactly(1);
    }

    @Test
    void aCoachOnAnExtraHatPlaysTheClipOfTheHomeworkTheyWrite() {
        // A parent account that coaches through an extra hat: only a coach_teams row, the user type stays PARENT.
        User parentCoach = parentOf(anna);
        jdbc.update("insert into coach_teams (coach_id, team_id) values (?, ?)", parentCoach.getId(), teamId);
        UUID clipId = clip(coach, "COMPLETED");

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                parentCoach.getId().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_COACH"), new SimpleGrantedAuthority("ROLE_PARENT"))));
        try {
            // Writing the homework and playing its clip go by the same coach_teams row.
            assertThat(homeworkService.create(teamId, upsert(List.of(clipItem(clipId))), parentCoach).items()).hasSize(1);
            assertThat(userAccessService.canAccessClip(parentCoach, clipId)).isTrue();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void anAcceptanceIsFinalAndOnlyThenShowsTheKeyToThePlayer() {
        HomeworkDetailResponse homework = publishNow(upsert(List.of(
                choice(HomeworkQuestionKind.SINGLE_CHOICE, List.of("A", "B"), List.of(1)))), anna);
        UUID id = assignmentOf(homework.id(), anna);
        handInChoice(id, homework.items().get(0).id(), 0);
        assertThat(assignmentViews.get(id, annaUser).items().get(0).correctOptions()).isNull();

        clock.set(NOW.plusMinutes(1));
        assertThat(review(id).status()).isEqualTo(COMPLETED);

        assertThat(assignmentViews.get(id, annaUser).items().get(0).correctOptions()).containsExactly(1);
        assertCode(() -> review(id), ConflictException.class, "error.homework.invalid_transition");
    }

    @Test
    void aClipOpensOnlyWhileThePlayerIsOnTheTeam() {
        UUID clipId = clip(coach, "COMPLETED");
        User parent = parentOf(anna);
        HomeworkDetailResponse homework = publishNow(upsert(List.of(clipItem(clipId))), anna);
        UUID id = assignmentOf(homework.id(), anna);
        setStatus(id, "COMPLETED", 1);
        assertThat(userAccessService.canAccessClip(annaUser, clipId)).isTrue();

        leaveRoster(anna);

        assertThat(userAccessService.canAccessClip(annaUser, clipId)).isFalse();
        assertThat(userAccessService.canAccessClip(parent, clipId)).isFalse();
    }

    private void handInChoice(UUID id, UUID item, int option) {
        handIn(id, annaUser, null, List.of(new HomeworkAnswerRequest(item, null, List.of(option), null)));
    }

    private AssignmentResponse review(UUID id) {
        return reviewService.review(id, new ReviewAssignmentRequest(null), coach);
    }
}
