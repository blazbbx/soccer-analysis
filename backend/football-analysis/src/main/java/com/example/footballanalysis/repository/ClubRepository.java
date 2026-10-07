package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.Club;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClubRepository extends JpaRepository<Club, UUID> {
    List<Club> findByCreatedByUserId(UUID createdByUserId);

    Page<Club> findAllByOrderByNameAscIdAsc(Pageable pageable);
}
