package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.responses.HomeworkDetailResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Parent and child hand in at the same moment. Not run by {@code mvn test}. */
class HomeworkSubmitLockPostgresIT extends HomeworkPostgresTestBase {

    @Test
    void twoHandInsGiveOneSuccessAndOneConflict() throws Exception {
        UUID anna = rosterProfile(teamId, "Anna");
        User player = linkedPlayer(anna);
        User parent = parentOf(anna);
        HomeworkDetailResponse homework = publishNow(upsert(List.of(text("x"))), anna);
        UUID id = assignmentOf(homework.id(), anna);
        answerService.markViewed(id, player);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = List.of(
                    pool.submit(() -> handIn(start, id, player)),
                    pool.submit(() -> handIn(start, id, parent)));
            start.countDown();
            List<String> outcomes = List.of(results.get(0).get(30, TimeUnit.SECONDS), results.get(1).get(30, TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder("ok", "error.homework.invalid_transition");
        } finally {
            pool.shutdownNow();
        }
        // Exactly one hand-in went through: the loser of the race wrote nothing.
        assertThat(jdbc.queryForObject("select count(*) from audit_events where event_type = 'HOMEWORK_SUBMITTED' and target_id = ?",
                Integer.class, id.toString())).isEqualTo(1);
    }

    private String handIn(CountDownLatch start, UUID id, User actor) throws InterruptedException {
        start.await();
        try {
            handIn(id, actor, "válasz", null);
            return "ok";
        } catch (ConflictException ex) {
            return ex.getMessageCode();
        }
    }
}
