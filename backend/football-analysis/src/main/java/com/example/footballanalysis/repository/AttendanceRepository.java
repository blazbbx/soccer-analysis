package com.example.footballanalysis.repository;

import com.example.footballanalysis.model.db.Attendance;
import com.example.footballanalysis.model.db.AttendanceId;
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
public interface AttendanceRepository extends JpaRepository<Attendance, AttendanceId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attendance from Attendance attendance where attendance.id = :id")
    Optional<Attendance> findByIdForUpdate(@Param("id") AttendanceId id);

    List<Attendance> findByEvent_Id(UUID eventId);

    @Query("""
            select attendance from Attendance attendance
            where attendance.event.id = :eventId
              and attendance.playerProfile.id in :profileIds
            """)
    List<Attendance> findByEventAndProfiles(
            @Param("eventId") UUID eventId,
            @Param("profileIds") Collection<UUID> profileIds);

    List<Attendance> findByEvent_IdIn(Collection<UUID> eventIds);
}
