package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.RideSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RideSeatRepository extends JpaRepository<RideSeat, UUID> {

    @Query("""
            select seat from RideSeat seat
            join fetch seat.offer offer
            join fetch offer.event event
            where seat.playerProfile.id = :profileId
              and event.team.id = :teamId
            """)
    List<RideSeat> findByProfileAndTeam(@Param("profileId") UUID profileId, @Param("teamId") UUID teamId);

    @Query("""
            select seat from RideSeat seat
            where seat.offer.event.id = :eventId
              and seat.playerProfile.id = :profileId
            """)
    Optional<RideSeat> findByEventAndProfile(@Param("eventId") UUID eventId, @Param("profileId") UUID profileId);

    List<RideSeat> findByOffer_Id(UUID offerId);

    @Query("""
            select count(seat) from RideSeat seat
            where seat.offer.id = :offerId
              and seat.playerProfile.id in (
                  select roster.playerProfile.id from TeamRoster roster where roster.team.id = :teamId
              )
              and exists (
                  select 1 from Attendance attendance
                  where attendance.event.id = :eventId
                    and attendance.playerProfile.id = seat.playerProfile.id
                    and attendance.status = com.example.footballanalysis.model.db.AttendanceStatus.GOING
              )
            """)
    long countGoingSeatsOnRoster(
            @Param("offerId") UUID offerId,
            @Param("eventId") UUID eventId,
            @Param("teamId") UUID teamId);

    // The offer is joined by (id, event_id), not its primary key, so Hibernate cannot take it from the
    // persistence context and would load it once per seat. Fetch it here.
    @Query("""
            select seat
            from RideSeat seat
            join fetch seat.offer offer
            join fetch seat.playerProfile
            where offer.event.id in :eventIds
            order by seat.id
            """)
    List<RideSeat> findFetchedByEventIds(@Param("eventIds") Collection<UUID> eventIds);

    @Query("""
            select seat
            from RideSeat seat
            where seat.offer.event.id = :eventId and seat.playerProfile.id in :profileIds
            """)
    List<RideSeat> findByEventAndProfiles(@Param("eventId") UUID eventId, @Param("profileIds") Collection<UUID> profileIds);
}
