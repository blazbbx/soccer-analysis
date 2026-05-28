package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.Clip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ClipRepository extends JpaRepository<Clip, UUID> {

	List<Clip> findAllByMatch_IdIn(Collection<UUID> matchIds);

	List<Clip> findAllByMatch_IdOrderByCreatedAtDesc(UUID matchId);

	java.util.Optional<Clip> findByIdAndMatch_Id(UUID clipId, UUID matchId);

	void deleteAllByMatch_IdIn(Collection<UUID> matchIds);

	@Query("SELECT c.match.id FROM Clip c WHERE c.id = :clipId")
	UUID findMatchIdByClipId(@Param("clipId") UUID clipId);

}
