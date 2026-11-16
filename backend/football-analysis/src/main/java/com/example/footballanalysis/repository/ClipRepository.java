package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.Clip;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ClipRepository extends JpaRepository<Clip, UUID> {

	List<Clip> findAllByMatch_IdIn(Collection<UUID> matchIds);

	@Query("""
			select clip from Clip clip
			where clip.match.id = :matchId
			order by clip.createdAt desc, clip.id desc
			""")
	List<Clip> findPageByMatchId(@Param("matchId") UUID matchId, Pageable pageable);

	long countByMatch_Id(UUID matchId);

	/** Renders queued before {@code before} that are still not finished (processing watchdog). */
	@Query("select clip.id from Clip clip where clip.renderStatus in :statuses and clip.renderRequestedAt < :before")
	List<UUID> findRenderIdsRequestedBefore(@Param("statuses") Collection<String> statuses,
			@Param("before") java.time.LocalDateTime before);

	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@Query("select clip from Clip clip where clip.id = :id")
	java.util.Optional<Clip> findByIdForUpdate(@Param("id") UUID id);

	/** Clips with their match and its two teams, in one query (chat clip cards). */
	@Query("""
			select clip from Clip clip
			left join fetch clip.match m
			left join fetch m.homeTeam
			left join fetch m.awayTeam
			where clip.id in :ids
			""")
	List<Clip> findAllWithMatchByIdIn(@Param("ids") Collection<UUID> ids);

	void deleteAllByMatch_IdIn(Collection<UUID> matchIds);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE Clip c SET c.createdBy = null WHERE c.createdBy.id = :userId")
	int clearCreatedByUserId(@Param("userId") UUID userId);

	@Query("SELECT c.match.id FROM Clip c WHERE c.id = :clipId")
	UUID findMatchIdByClipId(@Param("clipId") UUID clipId);

	/** The clip belongs to a match of this team, home or away. */
	@Query("""
			SELECT (count(c) > 0) FROM Clip c
			WHERE c.id = :clipId
			  AND (c.match.homeTeam.id = :teamId OR c.match.awayTeam.id = :teamId)
			""")
	boolean isClipOfTeam(@Param("clipId") UUID clipId, @Param("teamId") UUID teamId);

}
