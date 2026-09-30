package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.ClubAdminInvite;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClubAdminInviteRepository extends JpaRepository<ClubAdminInvite, UUID> {

    Optional<ClubAdminInvite> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invite from ClubAdminInvite invite where invite.token = :token")
    Optional<ClubAdminInvite> findByTokenForUpdate(@Param("token") String token);
}
