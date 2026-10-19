package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.ParentInvite;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParentInviteRepository extends JpaRepository<ParentInvite, UUID> {

    Optional<ParentInvite> findByToken(String token);

    List<ParentInvite> findByPlayerProfile_IdOrderByCreatedAtDesc(UUID profileId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invite from ParentInvite invite where invite.token = :token")
    Optional<ParentInvite> findByTokenForUpdate(@Param("token") String token);

    @Modifying
    @Query("delete from ParentInvite invite where invite.playerProfile.id = :profileId")
    int deleteByPlayerProfile_Id(@Param("profileId") UUID profileId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from ParentInvite invite
            where invite.expiresAt < :now
            """)
    int deleteExpiredBefore(@Param("now") LocalDateTime now);
}
