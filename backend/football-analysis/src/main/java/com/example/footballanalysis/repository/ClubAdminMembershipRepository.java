package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.ClubAdminMembership;
import com.example.footballanalysis.model.db.ClubAdminMembershipId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClubAdminMembershipRepository extends JpaRepository<ClubAdminMembership, ClubAdminMembershipId> {

    boolean existsByUser_IdAndClub_Id(UUID userId, UUID clubId);

    List<ClubAdminMembership> findByUser_Id(UUID userId);

    List<ClubAdminMembership> findByClub_Id(UUID clubId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select membership from ClubAdminMembership membership where membership.club.id = :clubId")
    List<ClubAdminMembership> findByClub_IdForUpdate(@Param("clubId") UUID clubId);
}
