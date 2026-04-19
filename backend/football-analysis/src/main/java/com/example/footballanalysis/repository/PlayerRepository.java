package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.user.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlayerRepository extends JpaRepository<Player, UUID> {
	Optional<Player> findByEmail(String email);

    @Modifying
    @Query(value = "DELETE FROM player_teams WHERE team_id = :teamId", nativeQuery = true)
    void removeAllPlayersFromTeam(@Param("teamId") UUID teamId);
}
