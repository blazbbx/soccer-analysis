package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.StorageDeletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StorageDeletionRepository extends JpaRepository<StorageDeletion, UUID> {

    /** The oldest queued deletions first, a batch at a time. */
    List<StorageDeletion> findTop500ByOrderByCreatedAtAsc();
}
