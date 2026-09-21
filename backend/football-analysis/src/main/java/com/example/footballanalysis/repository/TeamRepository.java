package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.Team;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {
    @Override
    @EntityGraph(attributePaths = {"players", "coaches", "club"})
    java.util.List<Team> findAll();

    @Override
    @EntityGraph(attributePaths = {"players", "coaches", "club"})
    java.util.Optional<Team> findById(UUID id);

    @EntityGraph(attributePaths = {"players", "coaches", "club"})
    java.util.List<Team> findByClub_Id(UUID clubId);

    @Query("select t.id from Team t")
    java.util.List<UUID> findAllTeamIds();

    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, UUID id);
}
