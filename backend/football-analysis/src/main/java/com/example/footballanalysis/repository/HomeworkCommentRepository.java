package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.HomeworkComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HomeworkCommentRepository extends JpaRepository<HomeworkComment, UUID> {

    @EntityGraph(attributePaths = {"author"})
    List<HomeworkComment> findByAssignment_IdOrderByCreatedAtAscIdAsc(UUID assignmentId);
}
