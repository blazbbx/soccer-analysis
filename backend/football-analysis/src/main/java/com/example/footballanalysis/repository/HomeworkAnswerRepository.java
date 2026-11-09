package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.HomeworkAnswer;
import com.example.footballanalysis.model.db.HomeworkAnswerId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HomeworkAnswerRepository extends JpaRepository<HomeworkAnswer, HomeworkAnswerId> {

    @Query("select answer from HomeworkAnswer answer where answer.id.assignmentId = :assignmentId")
    List<HomeworkAnswer> findByAssignmentId(@Param("assignmentId") UUID assignmentId);

    /** Before a new hand-in of work sent back: an answer left blank this time must not keep its old row. */
    @Modifying
    @Query("delete from HomeworkAnswer answer where answer.id.assignmentId = :assignmentId")
    void deleteByAssignmentId(@Param("assignmentId") UUID assignmentId);

    /** Assignment id, item id and the marked moment of every timestamp answer of one homework. */
    @Query("""
            select answer.id.assignmentId, answer.id.itemId, answer.timestampMs from HomeworkAnswer answer
            where answer.assignment.homework.id = :homeworkId and answer.timestampMs is not null
            """)
    List<Object[]> findTimestamps(@Param("homeworkId") UUID homeworkId);
}
