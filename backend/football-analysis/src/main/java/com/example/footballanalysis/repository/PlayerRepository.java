package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.user.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlayerRepository extends JpaRepository<Player, UUID> {
	Optional<Player> findByEmail(String email);
	Optional<Player> findByKeycloakId(String keycloakId);
}
