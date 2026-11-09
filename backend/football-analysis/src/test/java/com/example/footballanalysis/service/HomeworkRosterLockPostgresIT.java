package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.requests.PublishHomeworkRequest;
import com.example.footballanalysis.model.responses.HomeworkDetailResponse;
import com.example.footballanalysis.repository.TeamRepository;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Publishing and a roster removal at the same moment leave no new assignment on the removed player. The removal here
 * does the part of {@code TeamService.removePlayerFromTeam} that matters: the team lock, the release, the roster row.
 * Not run by {@code mvn test}.
 */
class HomeworkRosterLockPostgresIT extends HomeworkPostgresTestBase {

    @Autowired private TeamRepository teamRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @RepeatedTest(5)
    void theRemovedPlayerNeverKeepsAFreshAssignment() throws Exception {
        UUID anna = rosterProfile(teamId, "Anna");
        rosterProfile(teamId, "Bela");
        HomeworkDetailResponse draft = homeworkService.create(teamId, upsert(List.of(text("x"))), coach);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> publish = pool.submit(() -> {
                start.await();
                try {
                    homeworkService.publish(teamId, draft.id(), new PublishHomeworkRequest(false, List.of(anna), draft.updatedAt()), coach);
                } catch (NotFoundException expectedWhenTheRemovalWins) {
                    assertThat(expectedWhenTheRemovalWins.getMessageCode()).isEqualTo("error.player_profile.not_found");
                }
                return null;
            });
            Future<?> removal = pool.submit(() -> {
                start.await();
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    teamRepository.findByIdForUpdate(teamId);
                    recipientService.releaseForRosterRemoval(anna, teamId);
                    jdbc.update("delete from team_roster where player_profile_id = ? and team_id = ?", anna, teamId);
                });
                return null;
            });
            start.countDown();
            publish.get(30, TimeUnit.SECONDS);
            removal.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from homework_assignments where player_profile_id = ?", Integer.class, anna)).isZero();
    }
}
