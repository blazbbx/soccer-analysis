package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.Attendance;
import com.example.footballanalysis.model.db.AttendanceId;
import com.example.footballanalysis.model.db.AttendanceStatus;
import com.example.footballanalysis.model.db.PlayerParent;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.RideOffer;
import com.example.footballanalysis.model.db.RideSeat;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.TeamEvent;
import com.example.footballanalysis.model.db.TeamEventType;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.responses.TeamEventResponse;
import com.example.footballanalysis.model.responses.TravelPassengerResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TravelBoardTest {

    @Test
    void ownChildIsListedWithoutUsingASeat() {
        Team team = team();
        TeamEvent event = event(team);
        PlayerProfile child = profile("Ann", "Adams");
        PlayerProfile other = profile("Bea", "Brown");
        Parent parent = parent();
        RideOffer offer = offer(event, parent, 2);

        TeamEventResponse response = TravelBoard.assemble(
                event,
                team,
                List.of(child, other),
                Set.of(child.getId(), other.getId()),
                true,
                List.of(),
                List.of(going(event, child, true), going(event, other, true)),
                List.of(offer),
                Map.of(),
                List.of(link(parent, child)),
                true);

        assertThat(response.rides()).singleElement().satisfies(ride -> {
            assertThat(ride.seatsTotal()).isEqualTo(2);
            assertThat(ride.seatsTaken()).isZero();
            assertThat(ride.family()).extracting(TravelPassengerResponse::playerProfileId).containsExactly(child.getId());
        });
        assertThat(response.summary().unseatedGoingCount()).isEqualTo(1);
        assertThat(response.roster())
                .filteredOn(entry -> entry.playerProfileId().equals(child.getId()))
                .singleElement()
                .satisfies(entry -> assertThat(entry.parentDrives()).isTrue());
    }

    @Test
    void childSeatedElsewhereIsNotListedOnTheParentCar() {
        Team team = team();
        TeamEvent event = event(team);
        PlayerProfile child = profile("Ann", "Adams");
        Parent parent = parent();
        User coach = parent();
        RideOffer parentRide = offer(event, parent, 2);
        RideOffer coachRide = offer(event, coach, 2);
        RideSeat seat = seat(coachRide, child);

        TeamEventResponse response = TravelBoard.assemble(
                event,
                team,
                List.of(child),
                Set.of(child.getId()),
                false,
                List.of(),
                List.of(going(event, child, true)),
                List.of(parentRide, coachRide),
                Map.of(coachRide.getId(), List.of(seat)),
                List.of(link(parent, child)),
                true);

        assertThat(response.rides()).satisfies(rides -> {
            assertThat(rides.get(0).family()).isEmpty();
            assertThat(rides.get(0).seatsTaken()).isZero();
            assertThat(rides.get(1).seatsTaken()).isEqualTo(1);
            assertThat(rides.get(1).passengers()).extracting(TravelPassengerResponse::playerProfileId).containsExactly(child.getId());
        });
        assertThat(response.summary().unseatedGoingCount()).isZero();
    }

    @Test
    void parentDriveIsLeftOutOfTheUnseatedCount() {
        Team team = team();
        TeamEvent event = event(team);
        PlayerProfile child = profile("Ann", "Adams");
        Parent parent = parent();
        RideOffer offer = offer(event, parent, 2);

        TeamEventResponse response = TravelBoard.assemble(
                event,
                team,
                List.of(child),
                Set.of(child.getId()),
                false,
                List.of(),
                List.of(going(event, child, true)),
                List.of(offer),
                Map.of(),
                List.of(link(parent, child)),
                false);

        assertThat(response.summary().unseatedGoingCount()).isZero();
        assertThat(response.summary().capacityGap()).isZero();
        assertThat(response.rides()).singleElement().satisfies(ride -> {
            assertThat(ride.seatsTaken()).isZero();
            assertThat(ride.family()).extracting(TravelPassengerResponse::playerProfileId).containsExactly(child.getId());
        });
    }

    @Test
    void aDrivingParentBlocksEveryCarButReleaseOnlyBlocksThatCar() {
        PlayerProfile child = profile("Ann", "Adams");
        PlayerProfile other = profile("Bea", "Brown");
        Parent parent = parent();
        Parent bystander = parent();
        RideOffer parentRide = offer(event(team()), parent, 2);
        UUID otherCar = UUID.randomUUID();

        TravelBoard.ParentCars cars = TravelBoard.parentCars(
                List.of(link(parent, child), link(bystander, other)),
                List.of(parentRide));

        assertThat(cars.staysWithAnyDrivingParent(child.getId())).isTrue();
        assertThat(cars.onThisOffer(parentRide.getId(), child.getId())).isTrue();
        assertThat(cars.onThisOffer(otherCar, child.getId())).isFalse();
        assertThat(cars.staysWithAnyDrivingParent(other.getId())).isFalse();
        assertThat(cars.onThisOffer(parentRide.getId(), other.getId())).isFalse();
    }

    private static Team team() {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        return team;
    }

    private static TeamEvent event(Team team) {
        TeamEvent event = new TeamEvent();
        event.setId(UUID.randomUUID());
        event.setTeam(team);
        event.setType(TeamEventType.TRAINING);
        event.setStartsAt(LocalDateTime.parse("2026-05-01T18:00:00"));
        event.setMeetingAt(LocalDateTime.parse("2026-05-01T17:00:00"));
        event.setMeetingPoint("Kapu");
        return event;
    }

    private static PlayerProfile profile(String first, String last) {
        PlayerProfile profile = new PlayerProfile();
        profile.setId(UUID.randomUUID());
        profile.setFirstName(first);
        profile.setLastName(last);
        return profile;
    }

    private static Parent parent() {
        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setFirstName("Pat");
        parent.setLastName("Parent");
        return parent;
    }

    private static PlayerParent link(User parent, PlayerProfile child) {
        PlayerParent link = new PlayerParent();
        link.setUser(parent);
        link.setPlayerProfile(child);
        return link;
    }

    private static RideOffer offer(TeamEvent event, User driver, int seats) {
        RideOffer offer = new RideOffer();
        offer.setId(UUID.randomUUID());
        offer.setEvent(event);
        offer.setDriver(driver);
        offer.setSeatsTotal(seats);
        offer.setDeparturePlace("Kapu");
        offer.setDepartureAt(event.getMeetingAt());
        return offer;
    }

    private static RideSeat seat(RideOffer offer, PlayerProfile profile) {
        RideSeat seat = new RideSeat();
        seat.setId(UUID.randomUUID());
        seat.setOffer(offer);
        seat.setPlayerProfile(profile);
        return seat;
    }

    private static Attendance going(TeamEvent event, PlayerProfile profile, boolean wantsRide) {
        Attendance attendance = new Attendance();
        attendance.setId(new AttendanceId(event.getId(), profile.getId()));
        attendance.setPlayerProfile(profile);
        attendance.setStatus(AttendanceStatus.GOING);
        attendance.setWantsRide(wantsRide);
        return attendance;
    }
}
