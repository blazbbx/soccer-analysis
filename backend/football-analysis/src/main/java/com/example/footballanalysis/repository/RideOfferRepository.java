package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.RideOffer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RideOfferRepository extends JpaRepository<RideOffer, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select offer from RideOffer offer where offer.id = :id")
    Optional<RideOffer> findByIdForUpdate(@Param("id") UUID id);

    Optional<RideOffer> findByEvent_IdAndDriver_Id(UUID eventId, UUID driverId);

    @Query("""
            select offer from RideOffer offer
            where offer.driver.id = :driverId
              and offer.event.team.id = :teamId
            order by offer.id
            """)
    List<RideOffer> findByDriverAndTeam(@Param("driverId") UUID driverId, @Param("teamId") UUID teamId);

    Optional<RideOffer> findByIdAndEvent_Id(UUID id, UUID eventId);

    List<RideOffer> findByEvent_IdOrderByDepartureAtAscIdAsc(UUID eventId);

    List<RideOffer> findByEvent_IdInOrderByDepartureAtAscIdAsc(Collection<UUID> eventIds);
}
