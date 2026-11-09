package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.Homework;
import com.example.footballanalysis.model.db.HomeworkStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HomeworkRepository extends JpaRepository<Homework, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select homework from Homework homework where homework.id = :id")
    Optional<Homework> findByIdForUpdate(@Param("id") UUID id);

    /** Handing work in and reviewing it share this lock; archiving and deleting wait for them through the write lock. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select homework from Homework homework where homework.id = :id")
    Optional<Homework> findByIdForShare(@Param("id") UUID id);

    Page<Homework> findByTeam_IdAndStatus(UUID teamId, HomeworkStatus status, Pageable pageable);
}
