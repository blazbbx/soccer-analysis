package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Attendance;
import com.example.footballanalysis.model.db.AttendanceId;
import com.example.footballanalysis.model.db.AttendanceStatus;
import com.example.footballanalysis.model.db.CupMatch;
import com.example.footballanalysis.model.db.CupTeam;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.PlayerParent;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.RideOffer;
import com.example.footballanalysis.model.db.RideSeat;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.TeamEvent;
import com.example.footballanalysis.model.db.TeamRosterId;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.requests.BookSeatRequest;
import com.example.footballanalysis.model.requests.UpsertAttendanceRequest;
import com.example.footballanalysis.model.requests.UpsertRideOfferRequest;
import com.example.footballanalysis.model.requests.UpsertTeamEventRequest;
import com.example.footballanalysis.model.responses.TeamEventResponse;
import com.example.footballanalysis.paging.PageQuery;
import com.example.footballanalysis.repository.AttendanceRepository;
import com.example.footballanalysis.repository.CupMatchRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.RideOfferRepository;
import com.example.footballanalysis.repository.RideSeatRepository;
import com.example.footballanalysis.repository.TeamEventRepository;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.TeamRosterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamEventService {

    static final int PAST_PAGE_SIZE = 20;

    /** Upper bound of seats in one car (same as the request validation). */
    private static final int MAX_SEATS = 8;

    private final TeamRepository teamRepository;
    private final TeamEventRepository teamEventRepository;
    private final AttendanceRepository attendanceRepository;
    private final RideOfferRepository rideOfferRepository;
    private final RideSeatRepository rideSeatRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerParentRepository playerParentRepository;
    private final TeamRosterRepository teamRosterRepository;
    private final MatchRepository matchRepository;
    private final CupMatchRepository cupMatchRepository;
    private final UserAccessService userAccessService;
    private final TravelAccess travelAccess;
    private final AuditEventService auditEventService;
    private final Clock clock;

    /**
     * Upcoming events (from the start of today) and the newest {@link #PAST_PAGE_SIZE} earlier ones.
     * Older events come from {@link #listPastEvents}, so the board does not grow with every season.
     */
    @Transactional(readOnly = true)
    public List<TeamEventResponse> listEvents(UUID teamId, User actor) {
        Team team = requireTeam(teamId);
        userAccessService.requireTravelRead(actor, teamId);
        LocalDateTime startOfToday = LocalDate.now(clock).atStartOfDay();
        List<TeamEvent> events = new ArrayList<>(orEmpty(teamEventRepository.findUpcoming(teamId, startOfToday)));
        events.addAll(orEmpty(teamEventRepository.findPastBefore(
                teamId, startOfToday, PageRequest.of(0, PAST_PAGE_SIZE))));
        return toBoardResponses(team, events, actor);
    }

    /**
     * Earlier events, newest first. Without a cursor it starts before today; with one it continues
     * after the given event (its start time and id).
     */
    @Transactional(readOnly = true)
    public List<TeamEventResponse> listPastEvents(
            UUID teamId, LocalDateTime beforeStartsAt, UUID beforeId, Integer limit, User actor) {
        Team team = requireTeam(teamId);
        userAccessService.requireTravelRead(actor, teamId);
        int size = limit == null ? PAST_PAGE_SIZE : Math.max(1, Math.min(limit, PageQuery.MAX_SIZE));
        PageRequest page = PageRequest.of(0, size);
        List<TeamEvent> events = beforeStartsAt == null || beforeId == null
                ? teamEventRepository.findPastBefore(teamId, LocalDate.now(clock).atStartOfDay(), page)
                : teamEventRepository.findPastBeforeEvent(teamId, beforeStartsAt, beforeId, page);
        return toBoardResponses(team, orEmpty(events), actor);
    }

    private List<TeamEventResponse> toBoardResponses(Team team, List<TeamEvent> events, User actor) {
        UUID teamId = team.getId();
        if (events.isEmpty()) {
            return List.of();
        }
        List<PlayerProfile> roster = orEmpty(playerProfileRepository.findByTeam_Id(teamId));
        ViewContext view = viewContext(actor, team, roster);
        BoardData board = loadBoard(events.stream().map(TeamEvent::getId).toList());
        List<PlayerParent> links = parentLinksOnRoster(roster);
        return events.stream()
                .map(event -> toResponse(event, team, view, board, links, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamEventResponse getEvent(UUID teamId, UUID eventId, User actor) {
        Team team = requireTeam(teamId);
        userAccessService.requireTravelRead(actor, teamId);
        TeamEvent event = requireEvent(teamId, eventId);
        return detail(event, team, actor);
    }

    @Transactional
    public TeamEventResponse createEvent(UUID teamId, UpsertTeamEventRequest request, User actor) {
        Team team = requireTeam(teamId);
        userAccessService.requireTravelWrite(actor, teamId);
        TeamEvent event = new TeamEvent();
        event.setId(UUID.randomUUID());
        event.setTeam(team);
        event.setCreatedBy(actor);
        applyEvent(event, team, request);
        LocalDateTime now = now();
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        teamEventRepository.save(event);
        audit(actor, "TEAM_EVENT_CREATED", "TEAM_EVENT", event.getId(),
                "teamId=" + teamId + ",type=" + event.getType());
        log.atInfo()
                .setMessage("Created team event")
                .addKeyValue("teamId", teamId)
                .addKeyValue("eventId", event.getId())
                .log();
        return detail(event, team, actor);
    }

    @Transactional
    public TeamEventResponse updateEvent(UUID teamId, UUID eventId, UpsertTeamEventRequest request, User actor) {
        Team team = requireTeam(teamId);
        userAccessService.requireTravelWrite(actor, teamId);
        TeamEvent event = lockEvent(teamId, eventId);
        requireExpectedUpdatedAt(request == null ? null : request.expectedUpdatedAt(), event.getUpdatedAt());
        validateEventRequest(request);
        assertNoDepartureConflict(event.getId(), request.startsAt(), request.meetingAt());
        applyEvent(event, team, request);
        event.setUpdatedAt(now());
        teamEventRepository.save(event);
        audit(actor, "TEAM_EVENT_UPDATED", "TEAM_EVENT", event.getId(),
                "teamId=" + teamId + ",type=" + event.getType());
        log.atInfo()
                .setMessage("Updated team event")
                .addKeyValue("teamId", teamId)
                .addKeyValue("eventId", event.getId())
                .log();
        return detail(event, team, actor);
    }

    @Transactional
    public void deleteEvent(UUID teamId, UUID eventId, User actor) {
        requireTeam(teamId);
        userAccessService.requireTravelWrite(actor, teamId);
        TeamEvent event = requireEvent(teamId, eventId);
        teamEventRepository.delete(event);
        audit(actor, "TEAM_EVENT_DELETED", "TEAM_EVENT", eventId, "teamId=" + teamId);
        log.atInfo()
                .setMessage("Deleted team event")
                .addKeyValue("teamId", teamId)
                .addKeyValue("eventId", eventId)
                .log();
    }

    @Transactional
    public TeamEventResponse upsertAttendance(
            UUID teamId,
            UUID eventId,
            UUID playerProfileId,
            UpsertAttendanceRequest request,
            User actor) {
        Team team = requireTeam(teamId);
        userAccessService.requireTravelRead(actor, teamId);
        TeamEvent event = requireEvent(teamId, eventId);
        if (request == null || request.status() == null) {
            throw new BadRequestException("error.validation.invalid_request", new Object[0], "Attendance status is required.");
        }
        PlayerProfile profile = requireRosterProfile(teamId, playerProfileId);
        if (!travelAccess.canActForProfile(actor, team, profile)) {
            throw forbidden();
        }

        // Lock the profile before trusting the roster row. Removal locks the same row.
        lockProfile(playerProfileId);
        requireStillOnRoster(teamId, playerProfileId);
        if (request.status() != AttendanceStatus.GOING) {
            assertDriverStaysGoing(eventId, profile);
        }
        AttendanceId attendanceId = new AttendanceId(eventId, playerProfileId);
        Attendance attendance = attendanceRepository.findByIdForUpdate(attendanceId).orElse(null);
        if (attendance == null) {
            attendance = new Attendance();
            attendance.setId(attendanceId);
            attendance.setEvent(event);
            attendance.setPlayerProfile(profile);
        }
        boolean wantsRide = request.status() == AttendanceStatus.GOING
                && (request.wantsRide() != null ? request.wantsRide() : attendance.isWantsRide());
        attendance.setStatus(request.status());
        attendance.setWantsRide(wantsRide);
        if (request.comment() != null) {
            attendance.setComment(blankToNull(request.comment()));
        }
        attendance.setRespondedBy(actor);
        attendance.setRespondedAt(now());

        boolean seatReleased = false;
        if (!wantsRide) {
            seatReleased = releaseSeatOfProfile(eventId, playerProfileId, actor);
        }
        saveAttendance(attendance);
        audit(actor, "TRAVEL_RSVP_SET", "PLAYER_PROFILE", playerProfileId,
                "profileId=" + playerProfileId + ",status=" + request.status() + ",seatReleased=" + seatReleased);
        log.atInfo()
                .setMessage("Set travel RSVP")
                .addKeyValue("teamId", teamId)
                .addKeyValue("eventId", eventId)
                .addKeyValue("status", request.status())
                .log();
        return detail(event, team, actor);
    }

    @Transactional
    public TeamEventResponse createRide(UUID teamId, UUID eventId, UpsertRideOfferRequest request, User actor) {
        Team team = requireTeam(teamId);
        userAccessService.requireTravelRead(actor, teamId);
        requireEvent(teamId, eventId);
        travelAccess.requireCanCreateRide(actor, team);
        requireSeatCount(request);
        if (actor == null || actor.getId() == null) {
            throw forbidden();
        }
        if (rideOfferRepository.findByEvent_IdAndDriver_Id(eventId, actor.getId()).isPresent()) {
            throw rideExists();
        }

        TeamEvent lockedEvent = lockEvent(teamId, eventId);
        if (rideOfferRepository.findByEvent_IdAndDriver_Id(eventId, actor.getId()).isPresent()) {
            throw rideExists();
        }
        Departure departure = resolveDeparture(true, request, lockedEvent);

        RideOffer offer = new RideOffer();
        offer.setId(UUID.randomUUID());
        offer.setEvent(lockedEvent);
        offer.setDriver(actor);
        offer.setSeatsTotal(request.seatsTotal());
        offer.setDeparturePlace(departure.place());
        offer.setDepartureAt(departure.at());
        offer.setNotes(blankToNull(request.notes()));
        LocalDateTime now = now();
        offer.setCreatedAt(now);
        offer.setUpdatedAt(now);
        saveOffer(offer);
        audit(actor, "RIDE_OFFER_CREATED", "RIDE_OFFER", offer.getId(),
                "eventId=" + eventId + ",seatsTotal=" + offer.getSeatsTotal());
        log.atInfo()
                .setMessage("Created ride offer")
                .addKeyValue("teamId", teamId)
                .addKeyValue("eventId", eventId)
                .addKeyValue("offerId", offer.getId())
                .log();
        return detail(lockedEvent, team, actor);
    }

    @Transactional
    public TeamEventResponse updateRide(
            UUID teamId,
            UUID eventId,
            UUID offerId,
            UpsertRideOfferRequest request,
            User actor) {
        Team team = requireTeam(teamId);
        userAccessService.requireTravelRead(actor, teamId);
        requireEvent(teamId, eventId);
        travelAccess.requireCanCreateRide(actor, team);
        requireSeatCount(request);
        if (request.expectedUpdatedAt() == null) {
            throw expectedUpdatedAtRequired();
        }

        TeamEvent lockedEvent = lockEvent(teamId, eventId);
        RideOffer locked = lockOffer(eventId, offerId);
        if (!isDriver(actor, locked)) {
            throw forbidden();
        }
        requireExpectedUpdatedAt(request.expectedUpdatedAt(), locked.getUpdatedAt());
        Departure departure = resolveDeparture(false, request, lockedEvent);
        List<RideSeat> currentSeats = orEmpty(rideSeatRepository.findByOffer_Id(offerId));
        int projected = occupiedSeats(currentSeats, eventId, teamId);
        if (request.seatsTotal() < projected) {
            throw new ConflictException(
                    "error.travel.ride_too_small",
                    new Object[0],
                    "The seat count is smaller than the number of passengers.");
        }

        locked.setSeatsTotal(request.seatsTotal());
        locked.setDeparturePlace(departure.place());
        locked.setDepartureAt(departure.at());
        locked.setNotes(blankToNull(request.notes()));
        locked.setUpdatedAt(now());
        saveOffer(locked);
        audit(actor, "RIDE_OFFER_UPDATED", "RIDE_OFFER", locked.getId(),
                "eventId=" + eventId + ",seatsTotal=" + locked.getSeatsTotal());
        log.atInfo()
                .setMessage("Updated ride offer")
                .addKeyValue("teamId", teamId)
                .addKeyValue("eventId", eventId)
                .addKeyValue("offerId", locked.getId())
                .log();
        return detail(lockedEvent, team, actor);
    }

    @Transactional
    public void deleteRide(UUID teamId, UUID eventId, UUID offerId, User actor) {
        // The driver may always withdraw their own ride, even after leaving the team
        // (no travel-read or hat check), so a stale offer cannot get stuck on the board.
        requireTeam(teamId);
        requireEvent(teamId, eventId);
        RideOffer offer = requireOffer(eventId, offerId);
        if (!isDriver(actor, offer)) {
            throw forbidden();
        }
        // Lock order: passenger profiles, then the offer. The seats are re-read under the locks.
        List<UUID> passengerIds = orEmpty(rideSeatRepository.findByOffer_Id(offerId)).stream()
                .map(TeamEventService::profileId)
                .filter(Objects::nonNull)
                .toList();
        lockProfiles(passengerIds);
        RideOffer locked = lockOffer(eventId, offerId);
        if (!isDriver(actor, locked)) {
            throw forbidden();
        }
        deleteLockedOffer(locked, actor);
    }

    @Transactional
    public TeamEventResponse bookSeat(
            UUID teamId,
            UUID eventId,
            UUID offerId,
            BookSeatRequest request,
            User actor) {
        Team team = requireTeam(teamId);
        userAccessService.requireTravelRead(actor, teamId);
        TeamEvent event = requireEvent(teamId, eventId);
        if (request == null || request.playerProfileId() == null) {
            throw new BadRequestException("error.validation.invalid_request", new Object[0], "Player profile is required.");
        }
        PlayerProfile profile = requireRosterProfile(teamId, request.playerProfileId());
        if (!travelAccess.canActForProfile(actor, team, profile)) {
            throw forbidden();
        }
        // Lock order: profile, then offer. The roster row is read again under the profile lock.
        lockProfile(profile.getId());
        requireStillOnRoster(teamId, profile.getId());
        RideOffer locked = lockOffer(eventId, offerId);
        Attendance attendance = attendanceRepository.findById(new AttendanceId(eventId, profile.getId())).orElse(null);
        if (attendance == null || attendance.getStatus() != AttendanceStatus.GOING) {
            throw new ConflictException("error.travel.seat_not_going", new Object[0], "Only a player who is going can take a seat.");
        }
        if (!attendance.isWantsRide()) {
            throw new ConflictException("error.travel.seat_not_requested", new Object[0], "This player did not ask for a ride.");
        }
        if (isDriverProfile(locked, profile)) {
            throw driverNotPassenger();
        }

        RideSeat existing = rideSeatRepository.findByEventAndProfile(eventId, profile.getId()).orElse(null);
        if (existing != null) {
            UUID existingOfferId = existing.getOffer() != null ? existing.getOffer().getId() : null;
            if (offerId.equals(existingOfferId)) {
                return detail(event, team, actor);
            }
            throw seatTaken();
        }
        if (parentCars(eventId, profile.getId()).staysWithAnyDrivingParent(profile.getId())) {
            throw new ConflictException(
                    "error.travel.child_stays_with_parent",
                    new Object[0],
                    "This child sits in the parent's car.");
        }
        List<RideSeat> currentSeats = orEmpty(rideSeatRepository.findByOffer_Id(offerId));
        if (occupiedSeats(currentSeats, eventId, teamId) >= locked.getSeatsTotal()) {
            throw new ConflictException("error.travel.ride_full", new Object[0], "This ride is full.");
        }
        insertSeat(event, locked, profile, actor);
        log.atInfo()
                .setMessage("Booked ride seat")
                .addKeyValue("teamId", teamId)
                .addKeyValue("eventId", eventId)
                .addKeyValue("offerId", offerId)
                .log();
        return detail(event, team, actor);
    }

    @Transactional
    public void releaseSeat(UUID teamId, UUID eventId, UUID offerId, UUID playerProfileId, User actor) {
        Team team = requireTeam(teamId);
        userAccessService.requireTravelRead(actor, teamId);
        requireEvent(teamId, eventId);
        PlayerProfile profile = requireRosterProfile(teamId, playerProfileId);
        if (!travelAccess.canActForProfile(actor, team, profile)) {
            throw forbidden();
        }
        // Lock order: profile, then offer. The seat is read only after both locks.
        lockProfile(playerProfileId);
        RideOffer offer = lockOffer(eventId, offerId);
        RideSeat seat = rideSeatRepository.findByEventAndProfile(eventId, playerProfileId).orElse(null);
        if (seat == null || seat.getOffer() == null || !offerId.equals(seat.getOffer().getId())) {
            throw rideNotFound(offerId);
        }
        if (parentCars(eventId, playerProfileId).onThisOffer(offerId, playerProfileId)) {
            throw new ConflictException(
                    "error.travel.child_stays_with_parent",
                    new Object[0],
                    "This child sits in the parent's car.");
        }
        rideSeatRepository.delete(seat);
        audit(actor, "RIDE_SEAT_RELEASED", "RIDE_SEAT", seat.getId(), "profileId=" + playerProfileId);
        log.atInfo()
                .setMessage("Released ride seat")
                .addKeyValue("teamId", teamId)
                .addKeyValue("eventId", eventId)
                .addKeyValue("offerId", offerId)
                .log();
    }

    @Transactional
    public void releaseSeatsForRosterRemoval(UUID profileId, UUID teamId, User actor) {
        if (profileId == null || teamId == null) {
            return;
        }
        PlayerProfile profile = playerProfileRepository.findById(profileId).orElse(null);
        User linked = profile != null ? profile.getLinkedUser() : null;
        withdrawDriver(linked != null ? linked.getId() : null, teamId, profileId, actor);
    }

    /** Drops this user's cars on the team when the coach membership goes away. */
    @Transactional
    public void releaseOffersForCoachRemoval(UUID coachUserId, UUID teamId, User actor) {
        if (coachUserId == null || teamId == null) {
            return;
        }
        withdrawDriver(coachUserId, teamId, null, actor);
    }

    /**
     * Deletes the driver's offers on this team, and the given profile's seats.
     * Profiles are locked in id order before any offer lock, matching book and release.
     */
    private void withdrawDriver(UUID driverUserId, UUID teamId, UUID seatProfileId, User actor) {
        List<RideOffer> offers = driverUserId == null
                ? new ArrayList<>()
                : new ArrayList<>(orEmpty(rideOfferRepository.findByDriverAndTeam(driverUserId, teamId)));
        Set<UUID> profileIds = new HashSet<>();
        if (seatProfileId != null) {
            profileIds.add(seatProfileId);
        }
        for (RideOffer offer : offers) {
            if (offer.getId() == null) {
                continue;
            }
            for (RideSeat seat : orEmpty(rideSeatRepository.findByOffer_Id(offer.getId()))) {
                UUID passengerId = profileId(seat);
                if (passengerId != null) {
                    profileIds.add(passengerId);
                }
            }
        }
        lockProfiles(profileIds);
        Set<UUID> deletedSeats = new HashSet<>();
        if (seatProfileId != null) {
            List<RideSeat> seats = new ArrayList<>(orEmpty(rideSeatRepository.findByProfileAndTeam(seatProfileId, teamId)));
            seats.sort(Comparator.comparing(RideSeat::getId, Comparator.nullsLast(Comparator.naturalOrder())));
            for (RideSeat seat : seats) {
                deleteSeat(seat, actor, deletedSeats);
            }
        }
        offers.sort(Comparator.comparing(RideOffer::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        for (RideOffer offer : offers) {
            if (offer.getId() == null) {
                continue;
            }
            RideOffer locked = rideOfferRepository.findByIdForUpdate(offer.getId()).orElse(null);
            if (locked == null) {
                continue;
            }
            deleteLockedOffer(locked, actor, deletedSeats);
        }
    }

    private void deleteLockedOffer(RideOffer locked, User actor) {
        deleteLockedOffer(locked, actor, new HashSet<>());
    }

    private void deleteLockedOffer(RideOffer locked, User actor, Set<UUID> deletedSeats) {
        UUID offerId = locked.getId();
        UUID eventId = locked.getEvent() != null ? locked.getEvent().getId() : null;
        List<RideSeat> seats = new ArrayList<>(orEmpty(rideSeatRepository.findByOffer_Id(offerId)));
        seats.sort(Comparator.comparing(RideSeat::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        for (RideSeat seat : seats) {
            deleteSeat(seat, actor, deletedSeats);
        }
        rideOfferRepository.delete(locked);
        audit(actor, "RIDE_OFFER_DELETED", "RIDE_OFFER", offerId, "eventId=" + eventId);
        log.atInfo()
                .setMessage("Deleted ride offer")
                .addKeyValue("eventId", eventId)
                .addKeyValue("offerId", offerId)
                .log();
    }

    private void deleteSeat(RideSeat seat, User actor, Set<UUID> deletedSeats) {
        if (seat == null || (seat.getId() != null && !deletedSeats.add(seat.getId()))) {
            return;
        }
        rideSeatRepository.delete(seat);
        audit(actor, "RIDE_SEAT_RELEASED", "RIDE_SEAT", seat.getId(), "profileId=" + profileId(seat));
    }

    /** Takes the profile row lock (first step of every lock order) and returns the locked row if found. */
    private PlayerProfile lockProfile(UUID profileId) {
        return profileId == null ? null : playerProfileRepository.findByIdForUpdate(profileId).orElse(null);
    }

    private static boolean violates(Throwable throwable, String constraint) {
        String needle = constraint.toLowerCase(java.util.Locale.ROOT);
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException cve
                    && cve.getConstraintName() != null
                    && cve.getConstraintName().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        // Fallback for drivers that do not report the constraint name.
        return messages(throwable).toLowerCase(java.util.Locale.ROOT).contains(needle);
    }

    private void applyEvent(TeamEvent event, Team team, UpsertTeamEventRequest request) {
        validateEventRequest(request);
        event.setType(request.type());
        event.setStartsAt(request.startsAt());
        event.setMeetingAt(request.meetingAt());
        event.setVenue(blankToNull(request.venue()));
        event.setMeetingPoint(request.meetingPoint().trim());
        event.setNotes(blankToNull(request.notes()));
        event.setRsvpDeadline(request.rsvpDeadline());
        event.setVideoMatch(resolveVideoMatch(team, request.videoMatchId()));
        event.setCupMatch(resolveCupMatch(team, request.cupMatchId()));
    }

    private static void validateEventRequest(UpsertTeamEventRequest request) {
        if (request == null || request.startsAt() == null || request.type() == null) {
            throw new BadRequestException("error.validation.invalid_request", new Object[0], "Event type and start are required.");
        }
        if (request.meetingPoint() == null || request.meetingPoint().isBlank()) {
            throw new BadRequestException("validation.travel.meeting_point", new Object[0], "Meeting point is required.");
        }
        if (request.meetingAt() == null) {
            throw new BadRequestException("validation.travel.meeting_at", new Object[0], "Meeting time is required.");
        }
        if (request.meetingAt().isAfter(request.startsAt())) {
            throw new BadRequestException(
                    "error.travel.meeting_after_start",
                    new Object[0],
                    "The meeting time must be at or before the start.");
        }
        if (request.rsvpDeadline() != null && request.rsvpDeadline().isAfter(request.startsAt())) {
            throw new BadRequestException(
                    "error.travel.deadline_after_start",
                    new Object[0],
                    "The RSVP deadline must be at or before the start.");
        }
    }

    private Match resolveVideoMatch(Team team, UUID videoMatchId) {
        if (videoMatchId == null) {
            return null;
        }
        Match match = matchRepository.findById(videoMatchId)
                .orElseThrow(() -> new NotFoundException(
                        "error.match.not_found",
                        new Object[]{videoMatchId},
                        "Match not found: " + videoMatchId));
        boolean home = match.getHomeTeam() != null && team.getId().equals(match.getHomeTeam().getId());
        boolean away = match.getAwayTeam() != null && team.getId().equals(match.getAwayTeam().getId());
        if (!home && !away) {
            throw new BadRequestException(
                    "error.travel.video_match_team",
                    new Object[0],
                    "The video match is not for this team.");
        }
        return match;
    }

    private CupMatch resolveCupMatch(Team team, UUID cupMatchId) {
        if (cupMatchId == null) {
            return null;
        }
        CupMatch cupMatch = cupMatchRepository.findById(cupMatchId)
                .orElseThrow(() -> new NotFoundException(
                        "error.cupmatch.not_found",
                        new Object[]{cupMatchId},
                        "Cup match not found: " + cupMatchId));
        if (!cupSideIsTeam(cupMatch.getHomeTeam(), team.getId()) && !cupSideIsTeam(cupMatch.getAwayTeam(), team.getId())) {
            throw new BadRequestException(
                    "error.travel.cup_match_team",
                    new Object[0],
                    "The cup match is not for this team.");
        }
        return cupMatch;
    }

    private static boolean cupSideIsTeam(CupTeam side, UUID teamId) {
        return side != null && side.getRealTeam() != null && teamId.equals(side.getRealTeam().getId());
    }

    private void assertNoDepartureConflict(UUID eventId, LocalDateTime startsAt, LocalDateTime meetingAt) {
        for (RideOffer offer : orEmpty(rideOfferRepository.findByEvent_IdOrderByDepartureAtAscIdAsc(eventId))) {
            LocalDateTime departureAt = offer.getDepartureAt();
            if (departureAt == null || startsAt == null || meetingAt == null) {
                continue;
            }
            if (departureAt.isAfter(startsAt) || departureAt.isAfter(meetingAt)) {
                throw new ConflictException(
                        "error.travel.departure_conflicts",
                        new Object[0],
                        "The new start or meeting time is before an existing ride departure.");
            }
        }
    }

    private void insertSeat(TeamEvent event, RideOffer offer, PlayerProfile profile, User actor) {
        assertSeatEvent(event.getId(), offer);
        RideSeat seat = new RideSeat();
        seat.setId(UUID.randomUUID());
        seat.setOffer(offer);
        seat.setPlayerProfile(profile);
        seat.setBookedBy(actor);
        seat.setCreatedAt(now());
        try {
            rideSeatRepository.saveAndFlush(seat);
        } catch (DataIntegrityViolationException ex) {
            if (violates(ex, "ux_ride_seats_event_profile")) {
                throw seatTaken();
            }
            throw ex;
        }
        audit(actor, "RIDE_SEAT_BOOKED", "RIDE_SEAT", seat.getId(), "profileId=" + profile.getId());
    }

    private void assertDriverStaysGoing(UUID eventId, PlayerProfile profile) {
        User linked = profile.getLinkedUser();
        if (linked != null && linked.getId() != null
                && rideOfferRepository.findByEvent_IdAndDriver_Id(eventId, linked.getId()).isPresent()) {
            throw driverMustDeleteRide();
        }
    }

    private static ConflictException driverMustDeleteRide() {
        return new ConflictException(
                "error.travel.driver_must_delete_ride",
                new Object[0],
                "You are driving to this event. Delete your ride offer first.");
    }

    private boolean releaseSeatOfProfile(UUID eventId, UUID profileId, User actor) {
        RideSeat seat = rideSeatRepository.findByEventAndProfile(eventId, profileId).orElse(null);
        if (seat == null) {
            return false;
        }
        rideSeatRepository.delete(seat);
        audit(actor, "RIDE_SEAT_RELEASED", "RIDE_SEAT", seat.getId(), "profileId=" + profileId);
        return true;
    }

    private int occupiedSeats(List<RideSeat> seats, UUID eventId, UUID teamId) {
        return TravelBoard.takenSeats(seats, goingOnRoster(eventId, teamId, seats.stream()
                .map(TeamEventService::profileId)
                .filter(Objects::nonNull)
                .distinct()
                .toList()));
    }

    /** Links of this profile and every offer on the event, read after the row locks. */
    private TravelBoard.ParentCars parentCars(UUID eventId, UUID profileId) {
        return TravelBoard.parentCars(
                orEmpty(playerParentRepository.findByPlayerProfile_Id(profileId)),
                orEmpty(rideOfferRepository.findByEvent_IdOrderByDepartureAtAscIdAsc(eventId)));
    }

    private Set<UUID> goingOnRoster(UUID eventId, UUID teamId, List<UUID> profileIds) {
        if (profileIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> onRoster = new HashSet<>(orEmpty(teamRosterRepository.findProfileIdsOnTeam(teamId, profileIds)));
        Set<UUID> going = new HashSet<>();
        for (Map.Entry<UUID, Attendance> entry
                : indexAttendance(attendanceRepository.findByEventAndProfiles(eventId, profileIds)).entrySet()) {
            if (onRoster.contains(entry.getKey()) && statusOf(entry.getValue()) == AttendanceStatus.GOING) {
                going.add(entry.getKey());
            }
        }
        return going;
    }

    private Departure resolveDeparture(boolean create, UpsertRideOfferRequest request, TeamEvent event) {
        String place = blankToNull(request.departurePlace());
        if (place == null) {
            if (!create) {
                throw new BadRequestException("validation.travel.departure_place", new Object[0], "Departure place is required.");
            }
            place = event.getMeetingPoint();
        }
        LocalDateTime at = request.departureAt();
        if (at == null) {
            if (!create) {
                throw new BadRequestException("validation.travel.departure_at", new Object[0], "Departure time is required.");
            }
            at = event.getMeetingAt();
        }
        if (event.getStartsAt() != null && at.isAfter(event.getStartsAt())) {
            throw new BadRequestException(
                    "error.travel.departure_after_start",
                    new Object[0],
                    "The departure time must be at or before the start.");
        }
        if (event.getMeetingAt() != null && at.isAfter(event.getMeetingAt())) {
            throw new BadRequestException(
                    "error.travel.departure_after_meeting",
                    new Object[0],
                    "The departure time must be at or before the meeting time.");
        }
        return new Departure(place, at);
    }

    private TeamEventResponse detail(TeamEvent event, Team team, User actor) {
        List<PlayerProfile> roster = orEmpty(playerProfileRepository.findByTeam_Id(team.getId()));
        ViewContext view = viewContext(actor, team, roster);
        return toResponse(event, team, view, loadBoard(List.of(event.getId())), parentLinksOnRoster(roster), true);
    }

    /** Actor-dependent data, computed once per request instead of once per event. */
    private record ViewContext(
            List<PlayerProfile> roster,
            Set<UUID> rosterIds,
            boolean canCreateRide,
            List<PlayerProfile> callerProfiles) {
    }

    /** Attendance, offers and seats of several events, loaded with one query each. */
    private record BoardData(
            Map<UUID, List<Attendance>> attendanceByEvent,
            Map<UUID, List<RideOffer>> offersByEvent,
            Map<UUID, List<RideSeat>> seatsByOffer) {
    }

    private ViewContext viewContext(User actor, Team team, List<PlayerProfile> roster) {
        Set<UUID> rosterIds = new HashSet<>();
        for (PlayerProfile profile : roster) {
            if (profile.getId() != null) {
                rosterIds.add(profile.getId());
            }
        }
        List<PlayerParent> links = travelAccess.parentLinks(actor, team.getId());
        return new ViewContext(
                roster,
                rosterIds,
                travelAccess.canCreateRide(actor, team, links),
                travelAccess.callerProfiles(actor, team.getId(), roster, links));
    }

    private BoardData loadBoard(List<UUID> eventIds) {
        Map<UUID, List<Attendance>> attendanceByEvent = new HashMap<>();
        for (Attendance attendance : orEmpty(attendanceRepository.findByEvent_IdIn(eventIds))) {
            UUID eventId = attendance.getId() != null && attendance.getId().getEventId() != null
                    ? attendance.getId().getEventId()
                    : attendance.getEvent() != null ? attendance.getEvent().getId() : null;
            if (eventId != null) {
                attendanceByEvent.computeIfAbsent(eventId, key -> new ArrayList<>()).add(attendance);
            }
        }
        Map<UUID, List<RideOffer>> offersByEvent = new HashMap<>();
        for (RideOffer offer : orEmpty(rideOfferRepository.findByEvent_IdInOrderByDepartureAtAscIdAsc(eventIds))) {
            if (offer.getEvent() != null && offer.getEvent().getId() != null) {
                offersByEvent.computeIfAbsent(offer.getEvent().getId(), key -> new ArrayList<>()).add(offer);
            }
        }
        Map<UUID, List<RideSeat>> seatsByOffer = new HashMap<>();
        if (!offersByEvent.isEmpty()) {
            for (RideSeat seat : orEmpty(rideSeatRepository.findFetchedByEventIds(eventIds))) {
                if (seat.getOffer() != null && seat.getOffer().getId() != null) {
                    seatsByOffer.computeIfAbsent(seat.getOffer().getId(), key -> new ArrayList<>()).add(seat);
                }
            }
        }
        return new BoardData(attendanceByEvent, offersByEvent, seatsByOffer);
    }

    private List<PlayerParent> parentLinksOnRoster(List<PlayerProfile> roster) {
        List<UUID> rosterIds = roster.stream().map(PlayerProfile::getId).filter(Objects::nonNull).toList();
        if (rosterIds.isEmpty()) {
            return List.of();
        }
        return orEmpty(playerParentRepository.findByPlayerProfile_IdIn(rosterIds));
    }

    private TeamEventResponse toResponse(
            TeamEvent event,
            Team team,
            ViewContext view,
            BoardData board,
            List<PlayerParent> parentLinks,
            boolean includeRoster) {
        return TravelBoard.assemble(
                event,
                team,
                view.roster(),
                view.rosterIds(),
                view.canCreateRide(),
                view.callerProfiles(),
                board.attendanceByEvent().getOrDefault(event.getId(), List.of()),
                board.offersByEvent().getOrDefault(event.getId(), List.of()),
                board.seatsByOffer(),
                parentLinks,
                includeRoster);
    }

    private void lockProfiles(Iterable<UUID> profileIds) {
        Set<UUID> ordered = new HashSet<>();
        for (UUID profileId : profileIds) {
            if (profileId != null) {
                ordered.add(profileId);
            }
        }
        ordered.stream().sorted().forEach(this::lockProfile);
    }

    private PlayerProfile requireRosterProfile(UUID teamId, UUID profileId) {
        PlayerProfile profile = profileId == null ? null : playerProfileRepository.findById(profileId).orElse(null);
        if (profile == null || !onRoster(teamId, profileId)) {
            throw profileNotFound(profileId);
        }
        return profile;
    }

    /** Called only while the profile row lock is held, so a concurrent removal cannot sneak a seat in. */
    private void requireStillOnRoster(UUID teamId, UUID profileId) {
        if (!onRoster(teamId, profileId)) {
            throw profileNotFound(profileId);
        }
    }

    private boolean onRoster(UUID teamId, UUID profileId) {
        return profileId != null && teamRosterRepository.existsById(new TeamRosterId(profileId, teamId));
    }

    private static NotFoundException profileNotFound(UUID profileId) {
        return new NotFoundException(
                "error.player_profile.not_found",
                new Object[]{profileId},
                "Player profile not found.");
    }

    private Team requireTeam(UUID teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException(
                        "error.team.not_found",
                        new Object[]{teamId},
                        "Team not found."));
    }

    private TeamEvent requireEvent(UUID teamId, UUID eventId) {
        return teamEventRepository.findByIdAndTeam_Id(eventId, teamId)
                .orElseThrow(() -> eventNotFound(eventId));
    }

    private TeamEvent lockEvent(UUID teamId, UUID eventId) {
        TeamEvent event = teamEventRepository.findByIdForUpdate(eventId).orElseThrow(() -> eventNotFound(eventId));
        if (event.getTeam() == null || !teamId.equals(event.getTeam().getId())) {
            throw eventNotFound(eventId);
        }
        return event;
    }

    private RideOffer lockOffer(UUID eventId, UUID offerId) {
        RideOffer offer = rideOfferRepository.findByIdForUpdate(offerId).orElseThrow(() -> rideNotFound(offerId));
        if (offer.getEvent() == null || !eventId.equals(offer.getEvent().getId())) {
            throw rideNotFound(offerId);
        }
        return offer;
    }

    private RideOffer requireOffer(UUID eventId, UUID offerId) {
        return rideOfferRepository.findByIdAndEvent_Id(offerId, eventId)
                .orElseThrow(() -> rideNotFound(offerId));
    }

    private void saveAttendance(Attendance attendance) {
        try {
            attendanceRepository.saveAndFlush(attendance);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(
                    "error.travel.rsvp_conflict",
                    new Object[0],
                    "Attendance was already recorded for this player.");
        }
    }

    private void saveOffer(RideOffer offer) {
        try {
            rideOfferRepository.saveAndFlush(offer);
        } catch (DataIntegrityViolationException ex) {
            if (violates(ex, "ux_ride_offers_event_driver")) {
                throw rideExists();
            }
            throw ex;
        }
    }

    private void requireSeatCount(UpsertRideOfferRequest request) {
        if (request == null || request.seatsTotal() < 1 || request.seatsTotal() > MAX_SEATS) {
            throw new BadRequestException("validation.travel.seats_total", new Object[0], "Seat count must be between 1 and 8.");
        }
    }

    private static void requireExpectedUpdatedAt(LocalDateTime expected, LocalDateTime stored) {
        if (expected == null) {
            throw expectedUpdatedAtRequired();
        }
        if (!expected.equals(stored)) {
            throw new ConflictException(
                    "error.travel.stale_write",
                    new Object[0],
                    "The event or ride was updated by someone else.");
        }
    }

    private static void assertSeatEvent(UUID eventId, RideOffer offer) {
        UUID offerEventId = offer.getEvent() != null ? offer.getEvent().getId() : null;
        if (!eventId.equals(offerEventId)) {
            throw new BadRequestException(
                    "error.travel.seat_event_mismatch",
                    new Object[0],
                    "The seat does not belong to this ride's event.");
        }
    }

    private static boolean isDriver(User actor, RideOffer offer) {
        return actor != null
                && actor.getId() != null
                && offer.getDriver() != null
                && actor.getId().equals(offer.getDriver().getId());
    }

    private static boolean isDriverProfile(RideOffer offer, PlayerProfile profile) {
        return profile.getLinkedUser() != null
                && offer.getDriver() != null
                && profile.getLinkedUser().getId() != null
                && profile.getLinkedUser().getId().equals(offer.getDriver().getId());
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

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String messages(Throwable throwable) {
        StringBuilder text = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                text.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return text.toString();
    }

    private void audit(User actor, String eventType, String targetType, UUID targetId, String details) {
        auditEventService.record(
                eventType,
                actor != null ? actor.getId() : null,
                actor != null && actor.getRole() != null ? actor.getRole().name() : null,
                targetType,
                targetId != null ? targetId.toString() : null,
                details);
    }

    private static NotFoundException eventNotFound(UUID eventId) {
        return new NotFoundException("error.travel.event_not_found", new Object[]{eventId}, "Event not found on this team.");
    }

    private static NotFoundException rideNotFound(UUID offerId) {
        return new NotFoundException("error.travel.ride_not_found", new Object[]{offerId}, "Ride offer not found on this event.");
    }

    private static ForbiddenException forbidden() {
        return new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied.");
    }

    private static ConflictException rideExists() {
        return new ConflictException("error.travel.ride_exists", new Object[0], "This driver already has a ride offer for this event.");
    }

    private static ConflictException seatTaken() {
        return new ConflictException("error.travel.seat_taken", new Object[0], "This player already has a seat in another car.");
    }

    private static ConflictException driverNotPassenger() {
        return new ConflictException(
                "error.travel.driver_not_passenger",
                new Object[0],
                "The driver cannot sit as a passenger in their own car.");
    }

    private static BadRequestException expectedUpdatedAtRequired() {
        return new BadRequestException(
                "validation.travel.expected_updated_at",
                new Object[0],
                "The previous update time is required to save changes.");
    }

    private record Departure(String place, LocalDateTime at) {}
}
