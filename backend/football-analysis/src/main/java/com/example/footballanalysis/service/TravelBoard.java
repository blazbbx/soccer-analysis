package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.Attendance;
import com.example.footballanalysis.model.db.AttendanceStatus;
import com.example.footballanalysis.model.db.PlayerParent;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.RideOffer;
import com.example.footballanalysis.model.db.RideSeat;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.TeamEvent;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.responses.TeamEventResponse;
import com.example.footballanalysis.model.responses.TravelCallerProfileResponse;
import com.example.footballanalysis.model.responses.TravelPassengerResponse;
import com.example.footballanalysis.model.responses.TravelRideResponse;
import com.example.footballanalysis.model.responses.TravelRosterEntryResponse;
import com.example.footballanalysis.model.responses.TravelSummaryResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reads an already loaded travel board. It does not touch a repository.
 * Seat counts and "a parent is driving" both come from this class.
 */
final class TravelBoard {

    private static final Comparator<PlayerProfile> BY_NAME = Comparator
            .comparing(PlayerProfile::getLastName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(PlayerProfile::getFirstName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(PlayerProfile::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private TravelBoard() {
    }

    /** Distinct roster profiles on {@code seats} whose id is in {@code goingOnRoster}. */
    static int takenSeats(List<RideSeat> seats, Set<UUID> goingOnRoster) {
        Set<UUID> going = goingOnRoster == null ? Set.of() : goingOnRoster;
        Set<UUID> counted = new HashSet<>();
        int count = 0;
        for (RideSeat seat : orEmpty(seats)) {
            UUID profileId = profileId(seat);
            if (profileId == null || !going.contains(profileId)) {
                continue;
            }
            if (counted.add(profileId)) {
                count++;
            }
        }
        return count;
    }

    static ParentCars parentCars(List<PlayerParent> links, List<RideOffer> offers) {
        Map<UUID, UUID> offerIdByDriver = new HashMap<>();
        for (RideOffer offer : orEmpty(offers)) {
            if (offer.getId() != null && offer.getDriver() != null && offer.getDriver().getId() != null) {
                offerIdByDriver.putIfAbsent(offer.getDriver().getId(), offer.getId());
            }
        }
        return new ParentCars(orEmpty(links), offerIdByDriver);
    }

    static TeamEventResponse assemble(
            TeamEvent event,
            Team team,
            List<PlayerProfile> roster,
            Set<UUID> rosterIds,
            boolean callerCanOfferRide,
            List<PlayerProfile> callerProfiles,
            List<Attendance> attendances,
            List<RideOffer> offers,
            Map<UUID, List<RideSeat>> seatsByOffer,
            List<PlayerParent> parentLinks,
            boolean includeRoster) {
        List<PlayerProfile> people = roster == null ? List.of() : roster;
        Set<UUID> onRoster = rosterIds == null ? Set.of() : rosterIds;
        Map<UUID, Attendance> attendanceByProfile = indexAttendance(attendances);
        List<RideOffer> eventOffers = orEmpty(offers);
        Map<UUID, List<RideSeat>> seatsOf = seatsByOffer == null ? Map.of() : seatsByOffer;

        Map<UUID, UUID> offerByProfile = new HashMap<>();
        Map<UUID, Integer> takenByOffer = new HashMap<>();
        int freeSeats = 0;
        for (RideOffer offer : eventOffers) {
            List<RideSeat> seats = seatsOf.getOrDefault(offer.getId(), List.of());
            Set<UUID> goingHere = new HashSet<>();
            for (RideSeat seat : seats) {
                UUID profileId = profileId(seat);
                if (profileId == null) {
                    continue;
                }
                offerByProfile.putIfAbsent(profileId, offer.getId());
                if (onRoster.contains(profileId)
                        && statusOf(attendanceByProfile.get(profileId)) == AttendanceStatus.GOING) {
                    goingHere.add(profileId);
                }
            }
            int taken = takenSeats(seats, goingHere);
            takenByOffer.put(offer.getId(), taken);
            freeSeats += Math.max(0, offer.getSeatsTotal() - taken);
        }

        ParentCars cars = parentCars(parentLinks, eventOffers);
        Map<UUID, List<TravelPassengerResponse>> familyByOffer = cars.familyByOffer(attendanceByProfile, offerByProfile);

        int going = 0;
        int notGoing = 0;
        int maybe = 0;
        int unseatedGoing = 0;
        for (PlayerProfile profile : people) {
            Attendance attendance = attendanceByProfile.get(profile.getId());
            AttendanceStatus status = statusOf(attendance);
            if (status == AttendanceStatus.GOING) {
                going++;
                if (attendance != null && attendance.isWantsRide()
                        && !offerByProfile.containsKey(profile.getId())
                        && !cars.drives(profile.getId())) {
                    unseatedGoing++;
                }
            } else if (status == AttendanceStatus.NOT_GOING) {
                notGoing++;
            } else if (status == AttendanceStatus.MAYBE) {
                maybe++;
            }
        }
        int seatsTotal = eventOffers.stream().mapToInt(RideOffer::getSeatsTotal).sum();
        TravelSummaryResponse summary = new TravelSummaryResponse(
                going,
                notGoing,
                maybe,
                Math.max(0, people.size() - going - notGoing - maybe),
                seatsTotal,
                Math.max(0, unseatedGoing - freeSeats),
                unseatedGoing);

        List<TravelRideResponse> rides = eventOffers.stream()
                .map(offer -> toRide(
                        offer,
                        seatsOf.get(offer.getId()),
                        takenByOffer.getOrDefault(offer.getId(), 0),
                        includeRoster,
                        familyByOffer.getOrDefault(offer.getId(), List.of())))
                .toList();
        List<TravelRosterEntryResponse> rosterEntries = null;
        if (includeRoster) {
            rosterEntries = people.stream()
                    .sorted(BY_NAME)
                    .map(profile -> {
                        Attendance attendance = attendanceByProfile.get(profile.getId());
                        return new TravelRosterEntryResponse(
                                profile.getId(),
                                profile.getFirstName(),
                                profile.getLastName(),
                                statusOf(attendance),
                                attendance != null && attendance.isWantsRide(),
                                attendance != null ? attendance.getRespondedAt() : null,
                                attendance != null ? attendance.getComment() : null,
                                offerByProfile.get(profile.getId()),
                                cars.drives(profile.getId()));
                    })
                    .toList();
        }
        List<PlayerProfile> callers = callerProfiles == null ? List.of() : callerProfiles;
        return new TeamEventResponse(
                event.getId(),
                team.getId(),
                event.getType(),
                event.getStartsAt(),
                event.getMeetingAt(),
                event.getVenue(),
                event.getMeetingPoint(),
                event.getNotes(),
                event.getRsvpDeadline(),
                event.getVideoMatch() != null ? event.getVideoMatch().getId() : null,
                event.getCupMatch() != null ? event.getCupMatch().getId() : null,
                event.getUpdatedAt(),
                summary,
                callerCanOfferRide,
                callers.stream()
                        .map(profile -> new TravelCallerProfileResponse(
                                profile.getId(),
                                profile.getFirstName(),
                                profile.getLastName(),
                                statusOf(attendanceByProfile.get(profile.getId()))))
                        .toList(),
                rides,
                rosterEntries);
    }

    private static TravelRideResponse toRide(
            RideOffer offer,
            List<RideSeat> seats,
            int taken,
            boolean includePassengers,
            List<TravelPassengerResponse> family) {
        User driver = offer.getDriver();
        List<TravelPassengerResponse> passengers = null;
        if (includePassengers) {
            passengers = orEmpty(seats).stream()
                    .map(RideSeat::getPlayerProfile)
                    .filter(profile -> profile != null)
                    .sorted(BY_NAME)
                    .map(profile -> new TravelPassengerResponse(profile.getId(), profile.getFirstName(), profile.getLastName()))
                    .toList();
        }
        List<TravelPassengerResponse> withParent = new ArrayList<>(orEmpty(family));
        withParent.sort(Comparator
                .comparing(TravelPassengerResponse::lastName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(TravelPassengerResponse::firstName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return new TravelRideResponse(
                offer.getId(),
                driver != null ? driver.getId() : null,
                driver != null ? driver.getFirstName() : null,
                driver != null ? driver.getLastName() : null,
                offer.getSeatsTotal(),
                taken,
                offer.getDeparturePlace(),
                offer.getDepartureAt(),
                offer.getUpdatedAt(),
                offer.getNotes(),
                passengers,
                withParent);
    }

    private static Map<UUID, Attendance> indexAttendance(List<Attendance> attendances) {
        Map<UUID, Attendance> indexed = new HashMap<>();
        for (Attendance attendance : orEmpty(attendances)) {
            UUID profileId = attendance.getPlayerProfile() != null
                    ? attendance.getPlayerProfile().getId()
                    : attendance.getId() != null ? attendance.getId().getPlayerProfileId() : null;
            if (profileId != null) {
                indexed.put(profileId, attendance);
            }
        }
        return indexed;
    }

    private static AttendanceStatus statusOf(Attendance attendance) {
        if (attendance == null || attendance.getStatus() == null) {
            return AttendanceStatus.NO_RESPONSE;
        }
        return attendance.getStatus();
    }

    private static UUID profileId(RideSeat seat) {
        if (seat == null || seat.getPlayerProfile() == null) {
            return null;
        }
        return seat.getPlayerProfile().getId();
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    /**
     * One index of parent links and the offer each driver posted.
     * {@link #staysWithAnyDrivingParent} is any of the child's parents.
     * {@link #onThisOffer} is the driver of this offer.
     */
    static final class ParentCars {
        private final List<PlayerParent> links;
        private final Map<UUID, UUID> offerIdByDriver;
        private final Set<UUID> drivenChildren;

        private ParentCars(List<PlayerParent> links, Map<UUID, UUID> offerIdByDriver) {
            this.links = links;
            this.offerIdByDriver = offerIdByDriver;
            Set<UUID> driven = new HashSet<>();
            for (PlayerParent link : links) {
                UUID childId = childId(link);
                UUID parentId = parentId(link);
                if (childId != null && parentId != null && offerIdByDriver.containsKey(parentId)) {
                    driven.add(childId);
                }
            }
            this.drivenChildren = Set.copyOf(driven);
        }

        boolean staysWithAnyDrivingParent(UUID childId) {
            return childId != null && drivenChildren.contains(childId);
        }

        boolean onThisOffer(UUID offerId, UUID childId) {
            if (offerId == null || childId == null) {
                return false;
            }
            for (PlayerParent link : links) {
                if (!childId.equals(childId(link))) {
                    continue;
                }
                UUID parentId = parentId(link);
                if (parentId != null && offerId.equals(offerIdByDriver.get(parentId))) {
                    return true;
                }
            }
            return false;
        }

        boolean drives(UUID childId) {
            return staysWithAnyDrivingParent(childId);
        }

        private Map<UUID, List<TravelPassengerResponse>> familyByOffer(
                Map<UUID, Attendance> attendanceByProfile,
                Map<UUID, UUID> seatedOfferByProfile) {
            Map<UUID, List<TravelPassengerResponse>> familyByOffer = new HashMap<>();
            Map<UUID, Set<UUID>> listedOnOffer = new HashMap<>();
            if (offerIdByDriver.isEmpty()) {
                return familyByOffer;
            }
            for (PlayerParent link : links) {
                PlayerProfile child = link.getPlayerProfile();
                UUID childId = childId(link);
                UUID parentOfferId = offerIdByDriver.get(parentId(link));
                if (child == null || childId == null || parentOfferId == null) {
                    continue;
                }
                Attendance attendance = attendanceByProfile.get(childId);
                if (statusOf(attendance) != AttendanceStatus.GOING || attendance == null || !attendance.isWantsRide()) {
                    continue;
                }
                UUID seatedOfferId = seatedOfferByProfile.get(childId);
                if (seatedOfferId != null && !seatedOfferId.equals(parentOfferId)) {
                    continue;
                }
                if (!listedOnOffer.computeIfAbsent(parentOfferId, key -> new HashSet<>()).add(childId)) {
                    continue;
                }
                familyByOffer.computeIfAbsent(parentOfferId, key -> new ArrayList<>())
                        .add(new TravelPassengerResponse(child.getId(), child.getFirstName(), child.getLastName()));
            }
            return familyByOffer;
        }

        private static UUID childId(PlayerParent link) {
            PlayerProfile child = link.getPlayerProfile();
            return child == null ? null : child.getId();
        }

        private static UUID parentId(PlayerParent link) {
            User parent = link.getUser();
            return parent == null ? null : parent.getId();
        }
    }
}
