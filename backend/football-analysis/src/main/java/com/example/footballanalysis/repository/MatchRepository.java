package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.Team;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
public interface MatchRepository extends JpaRepository<Match, UUID> {

    @Override
    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "uploadedBy"})
    List<Match> findAll();

    @Override
    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "uploadedBy"})
    Optional<Match> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Match m where m.id = :id")
    Optional<Match> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "uploadedBy"})
    Optional<Match> findBySavedMinioFileName(String savedMinioFileName);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "uploadedBy"})
    List<Match> findAllByHomeTeam_IdOrAwayTeam_Id(UUID homeTeamId, UUID awayTeamId);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "uploadedBy"})
    List<Match> findAllByUploadedBy_Id(UUID uploadedById);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Match m SET m.uploadedBy = null WHERE m.uploadedBy.id = :userId")
    int clearUploadedByUserId(@Param("userId") UUID userId);


    default List<UUID> findTeamIdsByMatchId(UUID matchId) {
        return findById(matchId)
                .map(match -> Stream.of(match.getHomeTeam(), match.getAwayTeam())
                        .filter(Objects::nonNull)
                        .map(Team::getId)
                        .filter(Objects::nonNull)
                        .toList())
                .orElse(List.of());
    }
}