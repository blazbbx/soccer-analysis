package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.ClubMedicalAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubMedicalAgreementRepository extends JpaRepository<ClubMedicalAgreement, UUID> {

    /** Every agreement of the club, the newest first. */
    List<ClubMedicalAgreement> findByClubIdOrderByRecordedAtDescIdDesc(UUID clubId);

    /** The club's live agreement, locked: recording and withdrawing end it in the same transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ClubMedicalAgreement> findFirstByClubIdAndEndedAtIsNull(UUID clubId);

    Optional<ClubMedicalAgreement> findByIdAndClubId(UUID id, UUID clubId);
}
