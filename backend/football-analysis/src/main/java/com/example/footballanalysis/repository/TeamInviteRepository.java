package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.TeamInvite;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamInviteRepository extends JpaRepository<TeamInvite, UUID> {

    Optional<TeamInvite> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invite from TeamInvite invite where invite.token = :token")
    Optional<TeamInvite> findByTokenForUpdate(@Param("token") String token);

    boolean existsByToken(String token);

    List<TeamInvite> findAllByTeam_Id(UUID teamId);

    void deleteAllByTeam_Id(UUID teamId);
}