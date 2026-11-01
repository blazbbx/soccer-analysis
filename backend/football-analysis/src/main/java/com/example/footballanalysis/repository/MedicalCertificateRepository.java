package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.MedicalCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MedicalCertificateRepository extends JpaRepository<MedicalCertificate, UUID> {

    /** The one current certificate: completed, not superseded, not withdrawn. */
    @Query("""
            select c from MedicalCertificate c
            where c.playerProfileId = :profileId
              and c.completedAt is not null and c.supersededAt is null and c.withdrawnAt is null
            """)
    Optional<MedicalCertificate> findCurrentByProfileId(@Param("profileId") UUID profileId);

    @Query("""
            select c from MedicalCertificate c
            where c.playerProfileId in :profileIds
              and c.completedAt is not null and c.supersededAt is null and c.withdrawnAt is null
            """)
    List<MedicalCertificate> findCurrentByProfileIdIn(@Param("profileIds") Collection<UUID> profileIds);

    @Query("""
            select c from MedicalCertificate c
            where c.playerProfileId = :profileId
              and c.completedAt is null and c.supersededAt is null and c.withdrawnAt is null
            """)
    List<MedicalCertificate> findPendingByProfileId(@Param("profileId") UUID profileId);

    /** Superseded and withdrawn rows, newest first. Pending and current rows are not history. */
    @Query("""
            select c from MedicalCertificate c
            where c.playerProfileId = :profileId
              and c.completedAt is not null and (c.supersededAt is not null or c.withdrawnAt is not null)
            order by c.createdAt desc
            """)
    List<MedicalCertificate> findHistoryByProfileId(@Param("profileId") UUID profileId);

    @Query("select c from MedicalCertificate c where c.playerProfileId = :profileId and c.objectKey is not null")
    List<MedicalCertificate> findWithFileByProfileId(@Param("profileId") UUID profileId);

    @Query("""
            select distinct c.playerProfileId from MedicalCertificate c
            where c.completedAt is null and c.supersededAt is null and c.withdrawnAt is null
              and c.createdAt < :before
            """)
    List<UUID> findProfileIdsWithPendingBefore(@Param("before") Instant before);

    @Query("""
            select distinct c.playerProfileId from MedicalCertificate c
            where c.completedAt is not null and coalesce(c.withdrawnAt, c.supersededAt) < :closedBefore
            """)
    List<UUID> findProfileIdsWithHistoryClosedBefore(@Param("closedBefore") Instant closedBefore);
}
