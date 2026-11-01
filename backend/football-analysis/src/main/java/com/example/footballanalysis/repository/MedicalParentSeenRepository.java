package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.MedicalParentSeen;
import com.example.footballanalysis.model.db.MedicalParentSeenId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MedicalParentSeenRepository extends JpaRepository<MedicalParentSeen, MedicalParentSeenId> {

    @Query("select s from MedicalParentSeen s where s.id.parentUserId = :parentId and s.id.playerProfileId in :profileIds")
    List<MedicalParentSeen> findByParentAndProfileIdIn(@Param("parentId") UUID parentId, @Param("profileIds") Collection<UUID> profileIds);
}
