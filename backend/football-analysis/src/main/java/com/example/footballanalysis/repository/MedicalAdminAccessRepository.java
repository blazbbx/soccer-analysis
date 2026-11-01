package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.MedicalAdminAccess;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MedicalAdminAccessRepository extends JpaRepository<MedicalAdminAccess, UUID> {

    /** The rows shown to one club, newest first. Uses the GIN index on club_ids. */
    @Query(value = """
            select * from medical_admin_accesses
            where club_ids @> array[cast(:clubId as uuid)]
            order by created_at desc, id desc
            """,
            countQuery = "select count(*) from medical_admin_accesses where club_ids @> array[cast(:clubId as uuid)]",
            nativeQuery = true)
    Page<MedicalAdminAccess> findByClubId(@Param("clubId") UUID clubId, Pageable pageable);

    /** The rows of a profile that no club sees, newest first: the parent gets these instead. */
    @Query(value = """
            select * from medical_admin_accesses
            where player_profile_id = :profileId and cardinality(club_ids) = 0
            order by created_at desc, id desc
            """,
            nativeQuery = true)
    List<MedicalAdminAccess> findWithoutClubByProfileId(@Param("profileId") UUID profileId);

    /** The rows no club sees for these profiles: the parent's notice counts them. */
    @Query(value = """
            select * from medical_admin_accesses
            where player_profile_id in (:profileIds) and cardinality(club_ids) = 0
            """,
            nativeQuery = true)
    List<MedicalAdminAccess> findWithoutClubByProfileIdIn(@Param("profileIds") java.util.Collection<UUID> profileIds);

    /** The cleanup's batch delete after the audit retention. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from MedicalAdminAccess a where a.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") java.time.Instant cutoff);
}
