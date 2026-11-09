package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.HomeworkAssignment;
import com.example.footballanalysis.model.db.HomeworkAssignmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HomeworkAssignmentRepository extends JpaRepository<HomeworkAssignment, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select assignment from HomeworkAssignment assignment where assignment.id = :id")
    Optional<HomeworkAssignment> findByIdForUpdate(@Param("id") UUID id);

    /** Only the id, so the row is first loaded under its lock and not from a stale copy in the session. */
    @Query("select assignment.homework.id from HomeworkAssignment assignment where assignment.id = :id")
    Optional<UUID> findHomeworkIdById(@Param("id") UUID id);

    @Query("""
            select assignment from HomeworkAssignment assignment
            join fetch assignment.homework homework
            join fetch homework.team
            join fetch assignment.playerProfile
            where assignment.id = :id
            """)
    Optional<HomeworkAssignment> findDetailById(@Param("id") UUID id);

    /**
     * A player leaving the team takes all their work on it. The answers, comments and "seen" rows go with it (ON DELETE
     * CASCADE). A hand-in in progress holds the row lock, so this waits for it and then deletes the committed row.
     */
    @Modifying
    @Query("""
            delete from HomeworkAssignment assignment
            where assignment.playerProfile.id = :profileId
              and assignment.homework.id in (select homework.id from Homework homework where homework.team.id = :teamId)
            """)
    int deleteForRosterRemoval(@Param("profileId") UUID profileId, @Param("teamId") UUID teamId);

    /** The recipient's first opening. Conditional, so parent and child opening it together set it once. */
    @Modifying
    @Query("""
            update HomeworkAssignment assignment
            set assignment.firstViewedAt = :now, assignment.updatedAt = :now
            where assignment.id = :id and assignment.firstViewedAt is null
            """)
    int markFirstViewed(@Param("id") UUID id, @Param("now") LocalDateTime now);

    boolean existsByHomework_IdAndStatus(UUID homeworkId, HomeworkAssignmentStatus status);

    boolean existsByHomework_IdAndSubmittedAtIsNotNull(UUID homeworkId);


    Optional<HomeworkAssignment> findByHomework_IdAndPlayerProfile_Id(UUID homeworkId, UUID playerProfileId);

    @Query("select assignment.playerProfile.id from HomeworkAssignment assignment where assignment.homework.id = :homeworkId")
    List<UUID> findProfileIdsByHomeworkId(@Param("homeworkId") UUID homeworkId);

    @Query("""
            select assignment from HomeworkAssignment assignment
            join fetch assignment.playerProfile profile
            left join fetch profile.linkedUser
            left join fetch assignment.submittedBy
            where assignment.homework.id = :homeworkId
            order by lower(profile.lastName) asc, lower(profile.firstName) asc, assignment.id asc
            """)
    List<HomeworkAssignment> findRowsByHomeworkId(@Param("homeworkId") UUID homeworkId);

    /**
     * Homework id, assignment status, count, and how many of those are overdue, for one page of the coach's list.
     * The overdue condition is the same as {@code HomeworkDeadlines.overdue}.
     */
    @Query("""
            select homework.id, assignment.status, count(assignment),
                   sum(case when homework.status = com.example.footballanalysis.model.db.HomeworkStatus.PUBLISHED
                             and assignment.status in :open
                             and homework.dueAt < :now
                            then 1 else 0 end)
            from HomeworkAssignment assignment
            join assignment.homework homework
            where homework.id in :homeworkIds
            group by homework.id, assignment.status
            """)
    List<Object[]> countProgress(
            @Param("homeworkIds") Collection<UUID> homeworkIds,
            @Param("open") Collection<HomeworkAssignmentStatus> open,
            @Param("now") LocalDateTime now);

    // The recipient's tabs; every assignment is on exactly one of them. A draft has no assignments, and a player who left
    // the team has none either, so none of these has to check the publication or the roster.

    @Query(value = """
            select assignment from HomeworkAssignment assignment
            join fetch assignment.homework homework
            join fetch homework.team
            where assignment.playerProfile.id in :profileIds
              and homework.status = com.example.footballanalysis.model.db.HomeworkStatus.PUBLISHED
              and assignment.status in :todo
            order by homework.dueAt asc nulls last, assignment.id asc
            """, countQuery = """
            select count(assignment) from HomeworkAssignment assignment
            where assignment.playerProfile.id in :profileIds
              and assignment.homework.status = com.example.footballanalysis.model.db.HomeworkStatus.PUBLISHED
              and assignment.status in :todo
            """)
    Page<HomeworkAssignment> findTodoPage(
            @Param("profileIds") Collection<UUID> profileIds,
            @Param("todo") Collection<HomeworkAssignmentStatus> todo,
            Pageable pageable);

    @Query(value = """
            select assignment from HomeworkAssignment assignment
            join fetch assignment.homework homework
            join fetch homework.team
            where assignment.playerProfile.id in :profileIds
              and assignment.status = com.example.footballanalysis.model.db.HomeworkAssignmentStatus.SUBMITTED
            order by assignment.submittedAt desc, assignment.id asc
            """, countQuery = """
            select count(assignment) from HomeworkAssignment assignment
            where assignment.playerProfile.id in :profileIds
              and assignment.status = com.example.footballanalysis.model.db.HomeworkAssignmentStatus.SUBMITTED
            """)
    Page<HomeworkAssignment> findSubmittedPage(@Param("profileIds") Collection<UUID> profileIds, Pageable pageable);

    /** Everything that is neither on the todo tab nor waiting for the coach. */
    @Query(value = """
            select assignment from HomeworkAssignment assignment
            join fetch assignment.homework homework
            join fetch homework.team
            where assignment.playerProfile.id in :profileIds
              and assignment.status <> com.example.footballanalysis.model.db.HomeworkAssignmentStatus.SUBMITTED
              and (assignment.status = com.example.footballanalysis.model.db.HomeworkAssignmentStatus.COMPLETED or homework.status <> com.example.footballanalysis.model.db.HomeworkStatus.PUBLISHED)
            order by assignment.updatedAt desc, assignment.id asc
            """, countQuery = """
            select count(assignment) from HomeworkAssignment assignment
            where assignment.playerProfile.id in :profileIds
              and assignment.status <> com.example.footballanalysis.model.db.HomeworkAssignmentStatus.SUBMITTED
              and (assignment.status = com.example.footballanalysis.model.db.HomeworkAssignmentStatus.COMPLETED or assignment.homework.status <> com.example.footballanalysis.model.db.HomeworkStatus.PUBLISHED)
            """)
    Page<HomeworkAssignment> findDonePage(@Param("profileIds") Collection<UUID> profileIds, Pageable pageable);

    /**
     * The todo tab in numbers: count, overdue, and the nearest deadline still ahead. Always one row. Work sent back for a
     * fix is counted but has no deadline, the same as {@code HomeworkDeadlines.overdue}.
     */
    @Query("""
            select count(assignment),
                   coalesce(sum(case when homework.dueAt < :now and assignment.status <> com.example.footballanalysis.model.db.HomeworkAssignmentStatus.RETURNED
                                     then 1 else 0 end), 0),
                   min(case when homework.dueAt >= :now and assignment.status <> com.example.footballanalysis.model.db.HomeworkAssignmentStatus.RETURNED
                            then homework.dueAt end)
            from HomeworkAssignment assignment
            join assignment.homework homework
            where assignment.playerProfile.id in :profileIds
              and homework.status = com.example.footballanalysis.model.db.HomeworkStatus.PUBLISHED
              and assignment.status in :todo
            """)
    List<Object[]> summarizeTodo(
            @Param("profileIds") Collection<UUID> profileIds,
            @Param("todo") Collection<HomeworkAssignmentStatus> todo,
            @Param("now") LocalDateTime now);

    @Query("""
            select count(assignment) from HomeworkAssignment assignment
            where assignment.homework.team.id in :teamIds
              and assignment.status = com.example.footballanalysis.model.db.HomeworkAssignmentStatus.SUBMITTED
            """)
    long countToReviewOnTeams(@Param("teamIds") Collection<UUID> teamIds);
}
