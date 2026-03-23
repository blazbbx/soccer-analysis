package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.user.Coach;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CoachRepository extends JpaRepository<Coach, UUID> {
	Optional<Coach> findByEmail(String email);
	Optional<Coach> findByKeycloakId(String keycloakId);
}

