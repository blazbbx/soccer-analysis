package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Attendance;
import com.example.footballanalysis.model.db.AttendanceId;
import com.example.footballanalysis.model.db.AttendanceStatus;
import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.CupMatch;
import com.example.footballanalysis.model.db.CupTeam;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.PlayerParent;
import com.example.footballanalysis.model.db.PlayerParentId;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.RideOffer;
import com.example.footballanalysis.model.db.RideSeat;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.TeamEvent;
import com.example.footballanalysis.model.db.TeamEventType;
import com.example.footballanalysis.model.db.TeamRosterId;
import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.ClubAdmin;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.BookSeatRequest;
import com.example.footballanalysis.model.requests.UpsertAttendanceRequest;
import com.example.footballanalysis.model.requests.UpsertRideOfferRequest;
import com.example.footballanalysis.model.requests.UpsertTeamEventRequest;
import com.example.footballanalysis.model.responses.TeamEventResponse;
import com.example.footballanalysis.model.responses.TravelPassengerResponse;
import com.example.footballanalysis.model.responses.TravelRosterEntryResponse;
import com.example.footballanalysis.repository.AttendanceRepository;
import com.example.footballanalysis.repository.CupMatchRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.RideOfferRepository;
import com.example.footballanalysis.repository.RideSeatRepository;
import com.example.footballanalysis.repository.TeamEventRepository;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.TeamRosterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamEventServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private TeamEventRepository teamEventRepository;
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private RideOfferRepository rideOfferRepository;
    @Mock private RideSeatRepository rideSeatRepository;
    @Mock private PlayerProfileRepository playerProfileRepository;
    @Mock private PlayerParentRepository playerParentRepository;
    @Mock private TeamRosterRepository teamRosterRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private CupMatchRepository cupMatchRepository;
    @Mock private UserAccessService userAccessService;
    @Mock private TeamMembershipQuery teamMembershipQuery;
    @Mock private AuditEventService auditEventService;
    @InjectMocks private TeamEventService service;

    private Team team;
    private TeamEvent event;

    @BeforeEach
    void stubs() {
        // The fixtures use LocalDateTime.now(), so the service clock runs in the same default zone.
        ReflectionTestUtils.setField(service, "clock", Clock.systemDefaultZone());
        ReflectionTestUtils.setField(service, "travelAccess", new TravelAccess(
                userAccessService, playerProfileRepository, playerParentRepository, teamMembershipQuery));
        lenient().when(userAccessService.hasHat(any(), any())).thenAnswer(invocation -> {
            User actor = invocation.getArgument(0);
            UserRole role = invocation.getArgument(1);
            return actor != null && actor.getRole() == role;
        });
        lenient().when(userAccessService.isAdmin(any())).thenAnswer(invocation -> {
            User actor = invocation.getArgument(0);
            return actor != null && actor.getRole() == UserRole.ADMIN;
        });
        lenient().when(userAccessService.isClubAdmin(any(), any())).thenReturn(false);
        lenient().when(teamEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(rideOfferRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(rideSeatRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(attendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(playerProfileRepository.findByTeam_Id(any())).thenReturn(List.of());
        lenient().when(attendanceRepository.findByEvent_Id(any())).thenReturn(List.of());
        lenient().when(rideOfferRepository.findByEvent_IdOrderByDepartureAtAscIdAsc(any())).thenReturn(List.of());
        lenient().when(rideOfferRepository.findByEvent_IdAndDriver_Id(any(), any())).thenReturn(Optional.empty());
        lenient().when(rideSeatRepository.findByEventAndProfile(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void fullCarDoesNotSaveTheSecondSeat() {
        board();
        Admin admin = admin();
        PlayerProfile profile = profile("Ann", "Adams");
        RideOffer offer = offer(coachOnTeam(), 1);
        onRoster(profile);
        going(profile);
        PlayerProfile seated = profile("Bea", "Brown");
        when(rideOfferRepository.findByIdForUpdate(offer.getId())).thenReturn(Optional.of(offer));
        when(rideSeatRepository.findByOffer_Id(offer.getId())).thenReturn(List.of(seat(offer, seated)));
        when(teamRosterRepository.findProfileIdsOnTeam(any(), any())).thenReturn(List.of(seated.getId()));
        when(attendanceRepository.findByEventAndProfiles(any(), any())).thenReturn(List.of(goingRow(seated, true)));

        assertThatThrownBy(() -> service.bookSeat(team.getId(), event.getId(), offer.getId(), new BookSeatRequest(profile.getId()), admin))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.travel.ride_full");
        verify(rideSeatRepository, never()).saveAndFlush(any());
        verify(rideSeatRepository, never()).save(any());
    }

    @Test
    void createRideKeepsTheTypedSeatCountAndDoesNotSeatChildren() {
        board();
        Parent parent = parent();
        PlayerProfile child = profile("Ann", "Adams");
        link(parent, child);
        when(teamEventRepository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));

        service.createRide(team.getId(), event.getId(), rideRequest(1, null, null, null), parent);

        ArgumentCaptor<RideOffer> saved = ArgumentCaptor.forClass(RideOffer.class);
        verify(rideOfferRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getSeatsTotal()).isEqualTo(1);
        assertThat(saved.getValue().getDeparturePlace()).isEqualTo("Kapu");
        assertThat(saved.getValue().getDepartureAt()).isEqualTo(event.getMeetingAt());
        verify(rideSeatRepository, never()).saveAndFlush(any());
    }

    @Test
    void childIdsOnCreateDoNotCreateSeats() {
        board();
        Parent parent = parent();
        PlayerProfile checked = profile("Ann", "Adams");
        PlayerProfile skipped = profile("Bea", "Brown");
        link(parent, checked, skipped);
        when(teamEventRepository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));

        service.createRide(team.getId(), event.getId(), rideRequest(2, "Kapu", event.getMeetingAt(), null), parent);

        verify(rideOfferRepository).saveAndFlush(any());
        verify(rideSeatRepository, never()).saveAndFlush(any());
        verify(playerProfileRepository, never()).findById(skipped.getId());
        verify(playerProfileRepository, never()).findById(checked.getId());
    }

    @Test
    void emptyChildListSeatsNobody() {
        board();
        Parent parent = parent();
        link(parent, profile("Ann", "Adams"));
        when(teamEventRepository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));

        service.createRide(team.getId(), event.getId(), rideRequest(2, "Kapu", event.getMeetingAt(), null), parent);

        verify(rideOfferRepository).saveAndFlush(any());
        verify(rideSeatRepository, never()).saveAndFlush(any());
    }

    @Test
    void childAlreadySeatedElsewhereStillSavesTheOffer() {
        board();
        Parent parent = parent();
        PlayerProfile child = profile("Ann", "Adams");
        link(parent, child);
        when(teamEventRepository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));

        service.createRide(team.getId(), event.getId(), rideRequest(2, "Kapu", event.getMeetingAt(), null), parent);

        ArgumentCaptor<RideOffer> saved = ArgumentCaptor.forClass(RideOffer.class);
        verify(rideOfferRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getSeatsTotal()).isEqualTo(2);
        verify(rideSeatRepository, never()).saveAndFlush(any());
    }

    @Test
    void typedSeatCountIsStoredEvenWhenSeveralChildrenWantARide() {
        board();
        Parent parent = parent();
        PlayerProfile first = profile("Ann", "Adams");
        PlayerProfile second = profile("Bea", "Brown");
        link(parent, first, second);
        when(teamEventRepository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));

        service.createRide(
                team.getId(), event.getId(), rideRequest(1, "Kapu", event.getMeetingAt(), null), parent);

        ArgumentCaptor<RideOffer> saved = ArgumentCaptor.forClass(RideOffer.class);
        verify(rideOfferRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getSeatsTotal()).isEqualTo(1);
        verify(rideSeatRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateWithoutChildIdsDoesNotChangeSeats() {
        board();
        Parent parent = parent();
        PlayerProfile child = profile("Ann", "Adams");
        link(parent, child);
        RideOffer offer = offer(parent, 2);
        RideSeat seat = seat(offer, child);
        when(rideOfferRepository.findByIdForUpdate(offer.getId())).thenReturn(Optional.of(offer));
        when(rideSeatRepository.findByOffer_Id(offer.getId())).thenReturn(List.of(seat));

        service.updateRide(team.getId(), event.getId(), offer.getId(), rideRequest(2, "Masik", event.getMeetingAt(), offer.getUpdatedAt()), parent);

        verify(rideOfferRepository).saveAndFlush(any());
        verify(rideSeatRepository, never()).delete(any());
        verify(rideSeatRepository, never()).saveAndFlush(any());
    }

    @Test
    void secondOfferFromTheSameDriverIsRejectedBeforeSave() {
        board();
        Coach coach = coachOnTeam();
        RideOffer existing = offer(coach, 2);
        when(rideOfferRepository.findByEvent_IdAndDriver_Id(event.getId(), coach.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createRide(team.getId(), event.getId(), rideRequest(2, "Kapu", event.getMeetingAt(), null), coach))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.travel.ride_exists");
        verify(rideOfferRepository, never()).saveAndFlush(any());
        verify(rideOfferRepository, never()).save(any());
    }

    @Test
    void eventWithoutMeetingPlaceOrTimeIsRejected() {
        board();
        Coach coach = coachOnTeam();

        assertThatThrownBy(() -> service.createEvent(team.getId(), new UpsertTeamEventRequest(
                TeamEventType.TRAINING, event.getStartsAt(), event.getMeetingAt(), null, "  ", null, null, null, null, null), coach))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("messageCode", "validation.travel.meeting_point");
        assertThatThrownBy(() -> service.createEvent(team.getId(), new UpsertTeamEventRequest(
                TeamEventType.TRAINING, event.getStartsAt(), null, null, "Kapu", null, null, null, null, null), coach))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("messageCode", "validation.travel.meeting_at");
        verify(teamEventRepository, never()).save(any());
    }

    @Test
    void meetingAfterStartIsRejected() {
        board();
        assertThatThrownBy(() -> service.createEvent(team.getId(), new UpsertTeamEventRequest(
                TeamEventType.MATCH, event.getStartsAt(), event.getStartsAt().plusMinutes(1), null, "Kapu", null, null, null, null, null), coachOnTeam()))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.travel.meeting_after_start");
        verify(teamEventRepository, never()).save(any());
    }

    @Test
    void newRideCopiesMeetingAndRejectsALateDeparture() {
        board();
        Coach coach = coachOnTeam();
        when(teamEventRepository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));

        service.createRide(team.getId(), event.getId(), rideRequest(2, "Iskola", event.getMeetingAt().minusMinutes(10), null), coach);
        ArgumentCaptor<RideOffer> saved = ArgumentCaptor.forClass(RideOffer.class);
        verify(rideOfferRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getDeparturePlace()).isEqualTo("Iskola");
        assertThat(saved.getValue().getDepartureAt()).isEqualTo(event.getMeetingAt().minusMinutes(10));

        assertThatThrownBy(() -> service.createRide(team.getId(), event.getId(), rideRequest(2, "Kapu", event.getMeetingAt().plusMinutes(1), null), coach))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.travel.departure_after_meeting");
        assertThatThrownBy(() -> service.createRide(team.getId(), event.getId(), rideRequest(2, "Kapu", event.getStartsAt().plusMinutes(1), null), coach))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.travel.departure_after_start");
    }

    @Test
    void rideUpdateWithEmptyPlaceDoesNotSave() {
        board();
        Coach coach = coachOnTeam();
        RideOffer offer = offer(coach, 2);
        when(rideOfferRepository.findByIdForUpdate(offer.getId())).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.updateRide(
                team.getId(), event.getId(), offer.getId(), rideRequest(2, " ", offer.getDepartureAt(), offer.getUpdatedAt()), coach))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("messageCode", "validation.travel.departure_place");
        verify(rideOfferRepository, never()).saveAndFlush(any());
    }

    @Test
    void eventUpdateThatWouldPrecedeADepartureDoesNotSave() {
        board();
        Coach coach = coachOnTeam();
        RideOffer offer = offer(coach, 2);
        when(rideOfferRepository.findByEvent_IdOrderByDepartureAtAscIdAsc(event.getId())).thenReturn(List.of(offer));
        LocalDateTime earlier = offer.getDepartureAt().minusHours(2);

        assertThatThrownBy(() -> service.updateEvent(team.getId(), event.getId(), new UpsertTeamEventRequest(
                TeamEventType.TRAINING, earlier, earlier.minusMinutes(30), null, "Kapu", null, null, null, null, event.getUpdatedAt()), coach))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.travel.departure_conflicts");
        verify(teamEventRepository, never()).save(any());
        verify(rideOfferRepository, never()).saveAndFlush(any());
    }

    @Test
    void staleEventAndRideWritesDoNotSave() {
        board();
        Coach coach = coachOnTeam();
        assertThatThrownBy(() -> service.updateEvent(team.getId(), event.getId(), new UpsertTeamEventRequest(
                TeamEventType.TRAINING, event.getStartsAt(), event.getMeetingAt(), null, "Kapu", null, null, null, null, event.getUpdatedAt().minusMinutes(1)), coach))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.travel.stale_write");

        RideOffer offer = offer(coach, 2);
        when(rideOfferRepository.findByIdForUpdate(offer.getId())).thenReturn(Optional.of(offer));
        assertThatThrownBy(() -> service.updateRide(
                team.getId(), event.getId(), offer.getId(), rideRequest(2, "Kapu", offer.getDepartureAt(), offer.getUpdatedAt().minusMinutes(1)), coach))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.travel.stale_write");
        assertThatThrownBy(() -> service.updateRide(
                team.getId(), event.getId(), offer.getId(), rideRequest(2, "Kapu", offer.getDepartureAt(), null), coach))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("messageCode", "validation.travel.expected_updated_at");
        verify(teamEventRepository, never()).save(any());
        verify(rideOfferRepository, never()).saveAndFlush(any());

        service.createEvent(team.getId(), new UpsertTeamEventRequest(
                TeamEventType.TRAINING, event.getStartsAt(), event.getMeetingAt(), "  ", "Kapu", "  ", null, null, null, null), coach);
        verify(teamEventRepository).save(any());
    }

    @Test
    void rosterRemovalReleasesTheSeatAndKeepsAttendance() {
        UUID profileId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID otherTeamId = UUID.randomUUID();
        RideOffer offer = new RideOffer();
        offer.setId(UUID.randomUUID());
        RideSeat seat = new RideSeat();
        seat.setId(UUID.randomUUID());
        seat.setOffer(offer);
        PlayerProfile profile = profile("Ann", "Adams");
        profile.setId(profileId);
        seat.setPlayerProfile(profile);
        when(rideSeatRepository.findByProfileAndTeam(profileId, teamId)).thenReturn(List.of(seat));

        service.releaseSeatsForRosterRemoval(profileId, teamId, null);

        InOrder order = inOrder(playerProfileRepository, rideSeatRepository);
        order.verify(playerProfileRepository).findByIdForUpdate(profileId);
        order.verify(rideSeatRepository).delete(seat);
        verify(rideOfferRepository, never()).saveAndFlush(any());
        verify(rideSeatRepository, never()).findByProfileAndTeam(profileId, otherTeamId);
        verify(attendanceRepository, never()).save(any());
        verify(attendanceRepository, never()).saveAndFlush(any());
        verify(rideOfferRepository, never()).delete(any());
    }

    @Test
    void rosterRemovalDeletesTheLinkedDriversOffer() {
        board();
        Player player = player();
        PlayerProfile driverProfile = profile("Ann", "Adams");
        driverProfile.setLinkedUser(player);
        PlayerProfile passenger = profile("Bea", "Brown");
        RideOffer driven = offer(player, 3);
        RideSeat passengerSeat = seat(driven, passenger);
        RideSeat ownSeat = seat(offer(parent(), 2), driverProfile);
        when(playerProfileRepository.findById(driverProfile.getId())).thenReturn(Optional.of(driverProfile));
        when(rideOfferRepository.findByDriverAndTeam(player.getId(), team.getId())).thenReturn(List.of(driven));
        when(rideSeatRepository.findByOffer_Id(driven.getId())).thenReturn(List.of(passengerSeat));
        when(rideOfferRepository.findByIdForUpdate(driven.getId())).thenReturn(Optional.of(driven));
        when(rideSeatRepository.findByProfileAndTeam(driverProfile.getId(), team.getId())).thenReturn(List.of(ownSeat));

        service.releaseSeatsForRosterRemoval(driverProfile.getId(), team.getId(), null);

        verify(rideSeatRepository).delete(ownSeat);
        verify(rideSeatRepository).delete(passengerSeat);
        verify(rideOfferRepository).delete(driven);
        assertThat(driven.getSeatsTotal()).isEqualTo(3);
        verify(attendanceRepository, never()).save(any());
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void coachRemovalDeletesThatDriversOffersOnTheTeam() {
        board();
        Coach coach = coachOnTeam();
        PlayerProfile passenger = profile("Bea", "Brown");
        RideOffer driven = offer(coach, 2);
        RideSeat passengerSeat = seat(driven, passenger);
        when(rideOfferRepository.findByDriverAndTeam(coach.getId(), team.getId())).thenReturn(List.of(driven));
        when(rideSeatRepository.findByOffer_Id(driven.getId())).thenReturn(List.of(passengerSeat));
        when(rideOfferRepository.findByIdForUpdate(driven.getId())).thenReturn(Optional.of(driven));

        service.releaseOffersForCoachRemoval(coach.getId(), team.getId(), null);

        verify(rideSeatRepository).delete(passengerSeat);
        verify(rideOfferRepository).delete(driven);
        assertThat(driven.getSeatsTotal()).isEqualTo(2);
        verify(rideSeatRepository, never()).findByProfileAndTeam(any(), any());
    }

    @Test
    void bookSeatIsRejectedWhenThePlayerLeftTheRosterUnderTheLock() {
        board();
        PlayerProfile profile = profile("Ann", "Adams");
        when(playerProfileRepository.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(teamRosterRepository.existsById(new TeamRosterId(profile.getId(), team.getId()))).thenReturn(true, false);

        assertThatThrownBy(() -> service.bookSeat(
                team.getId(), event.getId(), offer(parent(), 2).getId(), new BookSeatRequest(profile.getId()), admin()))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.player_profile.not_found");
        verify(rideSeatRepository, never()).saveAndFlush(any());
    }

    @Test
    void attendanceIsRejectedWhenThePlayerLeftTheRosterUnderTheLock() {
        board();
        PlayerProfile profile = profile("Ann", "Adams");
        when(playerProfileRepository.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(teamRosterRepository.existsById(new TeamRosterId(profile.getId(), team.getId()))).thenReturn(true, false);

        assertThatThrownBy(() -> service.upsertAttendance(
                team.getId(), event.getId(), profile.getId(), new UpsertAttendanceRequest(AttendanceStatus.GOING, null), admin()))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.player_profile.not_found");
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void parentCanRsvpAfterTheDeadlineCoachCannotWriteAndClubAdminStillCan() {
        board();
        event.setRsvpDeadline(LocalDateTime.now().minusMinutes(5));
        PlayerProfile child = profile("Ann", "Adams");
        onRoster(child);
        Parent parent = parent();
        when(playerParentRepository.existsById(any())).thenReturn(true);

        service.upsertAttendance(
                team.getId(), event.getId(), child.getId(), new UpsertAttendanceRequest(AttendanceStatus.GOING, null), parent);

        assertThatThrownBy(() -> service.upsertAttendance(
                team.getId(), event.getId(), child.getId(), new UpsertAttendanceRequest(AttendanceStatus.GOING, null), coachOnTeam()))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.auth.forbidden");
        ClubAdmin clubAdmin = clubAdmin();
        when(userAccessService.isClubAdmin(clubAdmin, team.getClub().getId())).thenReturn(true);
        service.upsertAttendance(team.getId(), event.getId(), child.getId(), new UpsertAttendanceRequest(AttendanceStatus.MAYBE, null), clubAdmin);
        verify(attendanceRepository, org.mockito.Mockito.times(2)).saveAndFlush(any());
    }

    @Test
    void otherClubWriteIsForbiddenAndDoesNotUseTeamChat() {
        board();
        ClubAdmin clubAdmin = clubAdmin();
        doThrow(new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied."))
                .when(userAccessService).requireTravelWrite(clubAdmin, team.getId());

        assertThatThrownBy(() -> service.createEvent(team.getId(), eventBody(null), clubAdmin))
                .isInstanceOf(ForbiddenException.class);
        verify(teamEventRepository, never()).save(any());
        verify(userAccessService, never()).canAccessTeamChat(any(), any());
    }

    @Test
    void clubAdminCanWriteAnEventWithoutCoachMembership() {
        board();
        ClubAdmin clubAdmin = clubAdmin();
        when(userAccessService.isClubAdmin(clubAdmin, team.getClub().getId())).thenReturn(true);

        service.createEvent(team.getId(), eventBody(null), clubAdmin);

        verify(teamEventRepository).save(any());
        verify(userAccessService, never()).canAccessTeamChat(any(), any());
        verify(teamMembershipQuery, never()).isCoachOnTeam(any(), any());
    }

    @Test
    void playerCanRsvpAfterStart() {
        board();
        event.setStartsAt(LocalDateTime.now().minusMinutes(1));
        Player player = player();
        PlayerProfile profile = profile("Ann", "Adams");
        profile.setLinkedUser(player);
        onRoster(profile);

        service.upsertAttendance(
                team.getId(), event.getId(), profile.getId(), new UpsertAttendanceRequest(AttendanceStatus.GOING, null), player);

        verify(attendanceRepository).saveAndFlush(any());
    }

    @Test
    void playerCanRsvpAfterTheDeadline() {
        board();
        event.setRsvpDeadline(LocalDateTime.now().minusMinutes(1));
        Player player = player();
        PlayerProfile profile = profile("Ann", "Adams");
        profile.setLinkedUser(player);
        onRoster(profile);

        service.upsertAttendance(
                team.getId(), event.getId(), profile.getId(), new UpsertAttendanceRequest(AttendanceStatus.GOING, null), player);

        verify(attendanceRepository).saveAndFlush(any());
    }

    @Test
    void travelWriterCanStillAnswerAfterTheDeadlineAndStart() {
        board();
        event.setStartsAt(LocalDateTime.now().minusMinutes(1));
        event.setRsvpDeadline(LocalDateTime.now().minusHours(1));
        Admin admin = admin();
        PlayerProfile profile = profile("Ann", "Adams");
        onRoster(profile);

        service.upsertAttendance(
                team.getId(), event.getId(), profile.getId(), new UpsertAttendanceRequest(AttendanceStatus.GOING, null), admin);

        verify(attendanceRepository).saveAndFlush(any());
    }

    @Test
    void rideCanBeCreatedChangedOrDeletedAfterTheEventStarted() {
        board();
        event.setStartsAt(LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MICROS));
        event.setMeetingAt(event.getStartsAt().minusMinutes(30));
        Parent parent = parent();
        link(parent, profile("Ann", "Adams"));
        RideOffer offer = offer(parent, 2);
        when(rideOfferRepository.findByIdForUpdate(offer.getId())).thenReturn(Optional.of(offer));
        when(rideOfferRepository.findByIdAndEvent_Id(offer.getId(), event.getId())).thenReturn(Optional.of(offer));

        service.createRide(team.getId(), event.getId(), rideRequest(2, "Kapu", null, null), parent);
        service.updateRide(
                team.getId(), event.getId(), offer.getId(),
                rideRequest(2, "Kapu", offer.getDepartureAt(), offer.getUpdatedAt()), parent);
        service.deleteRide(team.getId(), event.getId(), offer.getId(), parent);

        verify(rideOfferRepository, org.mockito.Mockito.times(2)).saveAndFlush(any());
        verify(rideOfferRepository).delete(offer);
    }

    @Test
    void createRideStoresEightSeatsBesideOwnChildren() {
        board();
        Parent parent = parent();
        PlayerProfile child = profile("Ann", "Adams");
        link(parent, child);

        service.createRide(team.getId(), event.getId(), rideRequest(8, "Kapu", null, null), parent);

        ArgumentCaptor<RideOffer> saved = ArgumentCaptor.forClass(RideOffer.class);
        verify(rideOfferRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getSeatsTotal()).isEqualTo(8);
    }

    @Test
    void foreignRosterProfileIsForbiddenAndOffRosterProfileIsNotFound() {
        board();
        PlayerProfile foreign = profile("Ann", "Adams");
        onRoster(foreign);
        assertThatThrownBy(() -> service.upsertAttendance(
                team.getId(), event.getId(), foreign.getId(), new UpsertAttendanceRequest(AttendanceStatus.GOING, null), parent()))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.auth.forbidden");

        PlayerProfile missing = profile("Bea", "Brown");
        when(playerProfileRepository.findById(missing.getId())).thenReturn(Optional.of(missing));
        when(teamRosterRepository.existsById(new TeamRosterId(missing.getId(), team.getId()))).thenReturn(false);
        assertThatThrownBy(() -> service.upsertAttendance(
                team.getId(), event.getId(), missing.getId(), new UpsertAttendanceRequest(AttendanceStatus.GOING, null), coachOnTeam()))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.player_profile.not_found");
    }

    @Test
    void unknownTeamIsNotFoundAndExistingForeignTeamIsForbidden() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> service.listEvents(missing, parent()))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.team.not_found");
        verify(userAccessService, never()).requireTravelRead(any(), any());

        board();
        doThrow(new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied."))
                .when(userAccessService).requireTravelRead(any(), org.mockito.ArgumentMatchers.eq(team.getId()));
        assertThatThrownBy(() -> service.getEvent(team.getId(), event.getId(), parent()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void clubAdminCanReadOwnClubTeam() {
        board();
        when(teamEventRepository.findByTeam_IdOrderByStartsAtAscIdAsc(team.getId())).thenReturn(List.of(event));
        assertThat(service.listEvents(team.getId(), clubAdmin())).hasSize(1);

        doThrow(new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied."))
                .when(userAccessService).requireTravelRead(any(), org.mockito.ArgumentMatchers.eq(team.getId()));
        assertThatThrownBy(() -> service.listEvents(team.getId(), clubAdmin()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void playerWithoutTheFlagCannotOfferAndWithTheFlagDoesNotSeatChildren() {
        board();
        Player player = player();
        PlayerProfile own = profile("Ann", "Adams");
        own.setLinkedUser(player);
        own.setCanOfferRides(false);
        when(playerProfileRepository.existsByLinkedUser_IdAndTeam_Id(player.getId(), team.getId())).thenReturn(true);
        when(playerProfileRepository.findByLinkedUser_IdAndTeam_Id(player.getId(), team.getId())).thenReturn(Optional.of(own));

        assertThatThrownBy(() -> service.createRide(team.getId(), event.getId(), rideRequest(2, "Kapu", event.getMeetingAt(), null), player))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.travel.ride_not_allowed");
        verify(rideOfferRepository, never()).saveAndFlush(any());

        own.setCanOfferRides(true);
        when(teamEventRepository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        UUID foreignChild = UUID.randomUUID();
        service.createRide(team.getId(), event.getId(), rideRequest(2, "Kapu", event.getMeetingAt(), null), player);
        verify(rideOfferRepository).saveAndFlush(any());
        verify(rideSeatRepository, never()).saveAndFlush(any());
        verify(playerProfileRepository, never()).findById(foreignChild);
        verify(playerProfileRepository, never()).findByLinkedUser_Id(any());
    }

    @Test
    void adultPlayerWithoutAParentCanOfferWithoutTheFlag() {
        board();
        Player player = player();
        PlayerProfile own = linkedPlayer(player, LocalDate.now().minusYears(16), false);
        when(playerParentRepository.existsByPlayerProfile_Id(own.getId())).thenReturn(false);
        when(teamEventRepository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));

        service.createRide(team.getId(), event.getId(), rideRequest(2, "Kapu", event.getMeetingAt(), null), player);

        verify(rideOfferRepository).saveAndFlush(any());
    }

    @Test
    void underagePlayerWithoutAParentCannotOffer() {
        board();
        Player player = player();
        linkedPlayer(player, LocalDate.now().minusYears(15), false);

        assertThatThrownBy(() -> service.createRide(
                team.getId(), event.getId(), rideRequest(2, "Kapu", event.getMeetingAt(), null), player))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.travel.ride_not_allowed");
        verify(rideOfferRepository, never()).saveAndFlush(any());
    }

    @Test
    void adultPlayerWithAParentStillNeedsTheFlag() {
        board();
        Player player = player();
        PlayerProfile own = linkedPlayer(player, LocalDate.now().minusYears(17), false);
        when(playerParentRepository.existsByPlayerProfile_Id(own.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.createRide(
                team.getId(), event.getId(), rideRequest(2, "Kapu", event.getMeetingAt(), null), player))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.travel.ride_not_allowed");
        verify(rideOfferRepository, never()).saveAndFlush(any());
    }

    @Test
    void playerOffThisTeamCannotOfferEvenWithTheFlag() {
        board();
        Player player = player();
        PlayerProfile own = profile("Ann", "Adams");
        own.setLinkedUser(player);
        own.setCanOfferRides(true);
        when(playerProfileRepository.existsByLinkedUser_IdAndTeam_Id(player.getId(), team.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.createRide(team.getId(), event.getId(), rideRequest(2, "Kapu", event.getMeetingAt(), null), player))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.auth.forbidden");
        verify(rideOfferRepository, never()).saveAndFlush(any());
    }

    @Test
    void revokedPlayerCannotUpdateButCanStillDeleteTheirRide() {
        board();
        Player player = player();
        PlayerProfile own = profile("Ann", "Adams");
        own.setLinkedUser(player);
        own.setCanOfferRides(false);
        when(playerProfileRepository.existsByLinkedUser_IdAndTeam_Id(player.getId(), team.getId())).thenReturn(true);
        when(playerProfileRepository.findByLinkedUser_IdAndTeam_Id(player.getId(), team.getId())).thenReturn(Optional.of(own));
        RideOffer offer = offer(player, 2);
        when(rideOfferRepository.findByIdAndEvent_Id(offer.getId(), event.getId())).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.updateRide(
                team.getId(), event.getId(), offer.getId(), rideRequest(2, "Kapu", offer.getDepartureAt(), offer.getUpdatedAt()), player))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.travel.ride_not_allowed");
        verify(rideOfferRepository, never()).saveAndFlush(any());

        RideSeat seat = seat(offer, profile("Bea", "Brown"));
        when(rideSeatRepository.findByOffer_Id(offer.getId())).thenReturn(List.of(seat));
        when(rideOfferRepository.findByIdForUpdate(offer.getId())).thenReturn(Optional.of(offer));
        service.deleteRide(team.getId(), event.getId(), offer.getId(), player);
        verify(rideSeatRepository).delete(seat);
        verify(rideOfferRepository).delete(offer);
        verify(attendanceRepository, never()).save(any());
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void seatsCanBeBookedAndReleasedAfterTheEventStarted() {
        board();
        event.setStartsAt(LocalDateTime.now().minusMinutes(1));
        Player player = player();
        PlayerProfile own = profile("Ann", "Adams");
        own.setLinkedUser(player);
        onRoster(own);
        going(own);
        RideOffer offer = offer(parent(), 3);
        when(rideOfferRepository.findByIdForUpdate(offer.getId())).thenReturn(Optional.of(offer));

        service.bookSeat(team.getId(), event.getId(), offer.getId(), new BookSeatRequest(own.getId()), player);

        RideSeat taken = seat(offer, own);
        when(rideSeatRepository.findByEventAndProfile(event.getId(), own.getId())).thenReturn(Optional.of(taken));
        service.releaseSeat(team.getId(), event.getId(), offer.getId(), own.getId(), player);

        verify(rideSeatRepository).saveAndFlush(any());
        verify(rideSeatRepository).delete(taken);
    }

    @Test
    void rosterRemovalReleasesTheSeatWithoutChangingTheOfferedCount() {
        board();
        PlayerProfile child = profile("Ann", "Adams");
        RideOffer offer = offer(parent(), 3);
        RideSeat childSeat = seat(offer, child);
        when(rideSeatRepository.findByProfileAndTeam(child.getId(), team.getId())).thenReturn(List.of(childSeat));

        service.releaseSeatsForRosterRemoval(child.getId(), team.getId(), null);

        verify(rideSeatRepository).delete(childSeat);
        assertThat(offer.getSeatsTotal()).isEqualTo(3);
        verify(rideOfferRepository, never()).saveAndFlush(any());
    }

    @Test
    void driverWhoLostTravelAccessCanStillDeleteTheirRide() {
        board();
        Player player = player();
        RideOffer offer = offer(player, 2);
        when(rideOfferRepository.findByIdAndEvent_Id(offer.getId(), event.getId())).thenReturn(Optional.of(offer));
        when(rideOfferRepository.findByIdForUpdate(offer.getId())).thenReturn(Optional.of(offer));
        lenient().doThrow(new ForbiddenException("error.auth.forbidden", new Object[0], "no"))
                .when(userAccessService).requireTravelRead(any(), org.mockito.ArgumentMatchers.eq(team.getId()));

        service.deleteRide(team.getId(), event.getId(), offer.getId(), player);

        verify(rideOfferRepository).delete(offer);
        assertThatThrownBy(() -> service.deleteRide(team.getId(), event.getId(), offer.getId(), parent()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void driverCannotSitInTheirOwnCar() {
        board();
        Player player = player();
        PlayerProfile own = profile("Ann", "Adams");
        own.setLinkedUser(player);
        onRoster(own);
        going(own);
        RideOffer offer = offer(player, 2);
        when(rideOfferRepository.findByIdForUpdate(offer.getId())).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.bookSeat(team.getId(), event.getId(), offer.getId(), new BookSeatRequest(own.getId()), player))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.travel.driver_not_passenger");
        verify(rideSeatRepository, never()).saveAndFlush(any());
    }

    @Test
    void coachRideDoesNotSeatAnyoneAndAnotherTeamsCoachIsForbidden() {
        board();
        Coach coach = coachOnTeam();
        when(teamEventRepository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        service.createRide(team.getId(), event.getId(), rideRequest(3, null, null, null), coach);
        ArgumentCaptor<RideOffer> saved = ArgumentCaptor.forClass(RideOffer.class);
        verify(rideOfferRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getDeparturePlace()).isEqualTo(event.getMeetingPoint());
        verify(rideSeatRepository, never()).saveAndFlush(any());

        Coach other = new Coach();
        other.setId(UUID.randomUUID());
        other.setRole(UserRole.COACH);
        when(teamMembershipQuery.isCoachOnTeam(other.getId(), team.getId())).thenReturn(false);
        assertThatThrownBy(() -> service.createRide(team.getId(), event.getId(), rideRequest(2, "Kapu", event.getMeetingAt(), null), other))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.auth.forbidden");
    }

    @Test
    void clubAdminCanOfferInsideTheClubOnly() {
        board();
        ClubAdmin clubAdmin = clubAdmin();
        when(userAccessService.isClubAdmin(clubAdmin, team.getClub().getId())).thenReturn(true);
        when(teamEventRepository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        service.createRide(team.getId(), event.getId(), rideRequest(2, null, null, null), clubAdmin);
        verify(rideOfferRepository).saveAndFlush(any());
        verify(rideSeatRepository, never()).saveAndFlush(any());

        ClubAdmin other = clubAdmin();
        when(userAccessService.isClubAdmin(other, team.getClub().getId())).thenReturn(false);
        assertThatThrownBy(() -> service.createRide(team.getId(), event.getId(), rideRequest(2, "Kapu", event.getMeetingAt(), null), other))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.auth.forbidden");
    }

    @Test
    void goingChildDoesNotChangeTheParentsOfferedSeats() {
        board();
        Parent parent = parent();
        PlayerProfile child = profile("Ann", "Adams");
        onRoster(child);
        when(playerParentRepository.existsById(any())).thenReturn(true);
        RideOffer offer = offer(parent, 2);

        service.upsertAttendance(
                team.getId(), event.getId(), child.getId(), new UpsertAttendanceRequest(AttendanceStatus.GOING, null, true), parent);

        assertThat(offer.getSeatsTotal()).isEqualTo(2);
        verify(rideSeatRepository, never()).saveAndFlush(any());
        verify(attendanceRepository).saveAndFlush(any());
    }

    @Test
    void goingWithoutARideRequestDoesNotAddASeat() {
        board();
        Parent parent = parent();
        PlayerProfile child = profile("Ann", "Adams");
        onRoster(child);
        when(playerParentRepository.existsById(any())).thenReturn(true);
        RideOffer offer = offer(parent, 2);
        lenient().when(rideOfferRepository.findByEvent_IdAndDriver_Id(event.getId(), parent.getId())).thenReturn(Optional.of(offer));

        service.upsertAttendance(
                team.getId(), event.getId(), child.getId(), new UpsertAttendanceRequest(AttendanceStatus.GOING, null, false), parent);

        assertThat(offer.getSeatsTotal()).isEqualTo(2);
        verify(rideSeatRepository, never()).saveAndFlush(any());
        verify(rideOfferRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void decliningARideReleasesTheSeatWithoutChangingTheOfferedCount() {
        board();
        Parent parent = parent();
        PlayerProfile child = profile("Ann", "Adams");
        onRoster(child);
        RideOffer offer = offer(parent, 3);
        RideSeat childSeat = seat(offer, child);
        when(playerParentRepository.existsById(any())).thenReturn(true);
        when(rideSeatRepository.findByEventAndProfile(event.getId(), child.getId())).thenReturn(Optional.of(childSeat));

        service.upsertAttendance(
                team.getId(), event.getId(), child.getId(), new UpsertAttendanceRequest(AttendanceStatus.GOING, null, false), parent);

        assertThat(offer.getSeatsTotal()).isEqualTo(3);
        verify(rideSeatRepository).delete(childSeat);
        verify(rideOfferRepository, never()).saveAndFlush(any());
    }

    @Test
    void childMarkingGoingDoesNotGrowAnotherDriversCar() {
        board();
        Parent parent = parent();
        Player player = player();
        PlayerProfile child = profile("Ann", "Adams");
        child.setLinkedUser(player);
        onRoster(child);
        RideOffer offer = offer(parent, 2);
        lenient().when(rideOfferRepository.findByEvent_IdAndDriver_Id(event.getId(), parent.getId())).thenReturn(Optional.of(offer));

        service.upsertAttendance(
                team.getId(), event.getId(), child.getId(), new UpsertAttendanceRequest(AttendanceStatus.GOING, null, true), player);

        assertThat(offer.getSeatsTotal()).isEqualTo(2);
        verify(rideSeatRepository, never()).saveAndFlush(any());
        verify(rideOfferRepository, never()).findByIdForUpdate(any());
        verify(attendanceRepository).saveAndFlush(any());
    }

    @Test
    void otherParentRsvpDoesNotSeatTheChildInTheDrivingParentsCar() {
        board();
        Parent driving = parent();
        Parent other = parent();
        PlayerProfile child = profile("Ann", "Adams");
        onRoster(child);
        when(playerParentRepository.existsById(any())).thenReturn(true);
        RideOffer offer = offer(driving, 2);
        lenient().when(rideOfferRepository.findByEvent_IdAndDriver_Id(event.getId(), driving.getId())).thenReturn(Optional.of(offer));

        service.upsertAttendance(
                team.getId(), event.getId(), child.getId(), new UpsertAttendanceRequest(AttendanceStatus.GOING, null, true), other);

        assertThat(offer.getSeatsTotal()).isEqualTo(2);
        verify(rideSeatRepository, never()).saveAndFlush(any());
        verify(rideOfferRepository, never()).saveAndFlush(any());
    }

    @Test
    void fullCarStillSavesTheChildsAnswer() {
        board();
        Parent parent = parent();
        PlayerProfile child = profile("Ann", "Adams");
        onRoster(child);
        when(playerParentRepository.existsById(any())).thenReturn(true);
        RideOffer offer = offer(parent, 8);

        service.upsertAttendance(
                team.getId(), event.getId(), child.getId(), new UpsertAttendanceRequest(AttendanceStatus.GOING, null, true), parent);

        assertThat(offer.getSeatsTotal()).isEqualTo(8);
        verify(rideOfferRepository, never()).saveAndFlush(any());
        verify(rideSeatRepository, never()).saveAndFlush(any());
        verify(attendanceRepository).saveAndFlush(any());
    }

    @Test
    void drivingPlayerCannotSwitchToNotGoingOrMaybe() {
        board();
        Player player = player();
        PlayerProfile own = profile("Ann", "Adams");
        own.setLinkedUser(player);
        onRoster(own);
        RideOffer offer = offer(player, 2);
        when(rideOfferRepository.findByEvent_IdAndDriver_Id(event.getId(), player.getId())).thenReturn(Optional.of(offer));

        for (AttendanceStatus status : List.of(AttendanceStatus.NOT_GOING, AttendanceStatus.MAYBE)) {
            assertThatThrownBy(() -> service.upsertAttendance(
                    team.getId(), event.getId(), own.getId(), new UpsertAttendanceRequest(status, null), player))
                    .isInstanceOf(ConflictException.class)
                    .hasFieldOrPropertyWithValue("messageCode", "error.travel.driver_must_delete_ride");
        }
        verify(attendanceRepository, never()).saveAndFlush(any());
        verify(rideSeatRepository, never()).delete(any());
    }

    @Test
    void childCanDeclineWhileTheParentDrives() {
        board();
        Parent parent = parent();
        PlayerProfile first = profile("Ann", "Adams");
        onRoster(first);
        link(parent, first);
        when(playerParentRepository.existsById(any())).thenReturn(true);
        RideOffer offer = offer(parent, 2);

        service.upsertAttendance(
                team.getId(), event.getId(), first.getId(), new UpsertAttendanceRequest(AttendanceStatus.NOT_GOING, null), parent);

        assertThat(offer.getSeatsTotal()).isEqualTo(2);
        verify(attendanceRepository).saveAndFlush(any());
        verify(rideOfferRepository, never()).delete(any());
    }

    @Test
    void nullCommentKeepsTheOldCommentAndBlankClearsIt() {
        board();
        Player player = player();
        PlayerProfile own = profile("Ann", "Adams");
        own.setLinkedUser(player);
        onRoster(own);
        Attendance existing = goingRow(own, false);
        existing.setComment("Kesem");
        when(attendanceRepository.findByIdForUpdate(any())).thenReturn(Optional.of(existing));

        service.upsertAttendance(team.getId(), event.getId(), own.getId(), new UpsertAttendanceRequest(AttendanceStatus.GOING, null), player);
        assertThat(existing.getComment()).isEqualTo("Kesem");

        service.upsertAttendance(team.getId(), event.getId(), own.getId(), new UpsertAttendanceRequest(AttendanceStatus.GOING, "  "), player);
        assertThat(existing.getComment()).isNull();
    }

    @Test
    void missingSeatsCountOnlyUnseatedRiderNeedsBeyondFreeSeats() {
        board();
        PlayerProfile seated = profile("Ann", "Adams");
        PlayerProfile waitingOne = profile("Bea", "Brown");
        PlayerProfile waitingTwo = profile("Cid", "Cole");
        PlayerProfile walking = profile("Dan", "Dale");
        when(playerProfileRepository.findByTeam_Id(team.getId())).thenReturn(List.of(seated, waitingOne, waitingTwo, walking));
        when(attendanceRepository.findByEvent_IdIn(List.of(event.getId()))).thenReturn(List.of(
                goingRow(seated, true), goingRow(waitingOne, true), goingRow(waitingTwo, true), goingRow(walking, false)));
        RideOffer offer = offer(coachOnTeam(), 2);
        when(rideOfferRepository.findByEvent_IdInOrderByDepartureAtAscIdAsc(List.of(event.getId()))).thenReturn(List.of(offer));
        when(rideSeatRepository.findFetchedByEventIds(List.of(event.getId()))).thenReturn(List.of(seat(offer, seated)));

        TeamEventResponse response = service.getEvent(team.getId(), event.getId(), admin());

        assertThat(response.summary().unseatedGoingCount()).isEqualTo(2);
        assertThat(response.summary().seatsTotal()).isEqualTo(2);
        assertThat(response.summary().capacityGap()).isEqualTo(1);
        assertThat(response.rides()).singleElement().satisfies(ride -> assertThat(ride.seatsTaken()).isEqualTo(1));
        verify(rideSeatRepository, never()).countGoingSeatsOnRoster(any(), any(), any());
    }

    @Test
    void notGoingReleasesTheSeat() {
        board();
        PlayerProfile child = profile("Ann", "Adams");
        onRoster(child);
        RideOffer offer = offer(coachOnTeam(), 2);
        RideSeat seat = seat(offer, child);
        when(rideSeatRepository.findByEventAndProfile(event.getId(), child.getId())).thenReturn(Optional.of(seat));
        ClubAdmin clubAdmin = clubAdmin();
        when(userAccessService.isClubAdmin(clubAdmin, team.getClub().getId())).thenReturn(true);

        service.upsertAttendance(team.getId(), event.getId(), child.getId(), new UpsertAttendanceRequest(AttendanceStatus.NOT_GOING, null), clubAdmin);

        verify(rideSeatRepository).delete(seat);
        verify(attendanceRepository).saveAndFlush(any());
    }

    @Test
    void notGoingReleasesTheSeatWithoutChangingTheOfferedCount() {
        board();
        Parent parent = parent();
        PlayerProfile child = profile("Ann", "Adams");
        onRoster(child);
        RideOffer offer = offer(parent, 3);
        RideSeat childSeat = seat(offer, child);
        when(playerParentRepository.existsById(any())).thenReturn(true);
        when(rideSeatRepository.findByEventAndProfile(event.getId(), child.getId())).thenReturn(Optional.of(childSeat));

        service.upsertAttendance(
                team.getId(), event.getId(), child.getId(), new UpsertAttendanceRequest(AttendanceStatus.NOT_GOING, null), parent);

        assertThat(offer.getSeatsTotal()).isEqualTo(3);
        verify(rideSeatRepository).delete(childSeat);
        verify(rideOfferRepository, never()).saveAndFlush(any());
    }

    @Test
    void childWhoseParentDrivesCannotBeBookedIntoAnotherCar() {
        board();
        Parent parent = parent();
        PlayerProfile child = profile("Ann", "Adams");
        onRoster(child);
        going(child);
        when(playerParentRepository.existsById(new PlayerParentId(parent.getId(), child.getId()))).thenReturn(true);
        PlayerParent childLink = new PlayerParent();
        childLink.setUser(parent);
        childLink.setPlayerProfile(child);
        when(playerParentRepository.findByPlayerProfile_Id(child.getId())).thenReturn(List.of(childLink));
        RideOffer parentRide = offer(parent, 2);
        when(rideOfferRepository.findByEvent_IdOrderByDepartureAtAscIdAsc(event.getId())).thenReturn(List.of(parentRide));
        Coach coach = coachOnTeam();
        RideOffer other = offer(coach, 2);
        when(rideOfferRepository.findByIdForUpdate(other.getId())).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.bookSeat(
                team.getId(), event.getId(), other.getId(), new BookSeatRequest(child.getId()), parent))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.travel.child_stays_with_parent");
        verify(rideSeatRepository, never()).saveAndFlush(any());
    }

    @Test
    void parentCannotBookOwnChildIntoTheAdvertisedSeats() {
        board();
        Parent parent = parent();
        PlayerProfile child = profile("Ann", "Adams");
        onRoster(child);
        going(child);
        when(playerParentRepository.existsById(any())).thenReturn(true);
        PlayerParent childLink = new PlayerParent();
        childLink.setUser(parent);
        childLink.setPlayerProfile(child);
        when(playerParentRepository.findByPlayerProfile_Id(child.getId())).thenReturn(List.of(childLink));
        RideOffer parentRide = offer(parent, 2);
        when(rideOfferRepository.findByEvent_IdOrderByDepartureAtAscIdAsc(event.getId())).thenReturn(List.of(parentRide));
        when(rideOfferRepository.findByIdForUpdate(parentRide.getId())).thenReturn(Optional.of(parentRide));

        assertThatThrownBy(() -> service.bookSeat(
                team.getId(), event.getId(), parentRide.getId(), new BookSeatRequest(child.getId()), parent))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.travel.child_stays_with_parent");
        verify(rideSeatRepository, never()).saveAndFlush(any());
    }

    @Test
    void childWhoWantsARideIsListedOnTheParentsCarWithoutUsingASeat() {
        board();
        Parent parent = parent();
        PlayerProfile child = profile("Ann", "Adams");
        PlayerProfile other = profile("Bea", "Brown");
        when(playerProfileRepository.findByTeam_Id(team.getId())).thenReturn(List.of(child, other));
        when(attendanceRepository.findByEvent_IdIn(List.of(event.getId()))).thenReturn(List.of(
                goingRow(child, true), goingRow(other, true)));
        RideOffer offer = offer(parent, 2);
        when(rideOfferRepository.findByEvent_IdInOrderByDepartureAtAscIdAsc(List.of(event.getId()))).thenReturn(List.of(offer));
        PlayerParent link = new PlayerParent();
        link.setUser(parent);
        link.setPlayerProfile(child);
        when(playerParentRepository.findByPlayerProfile_IdIn(any())).thenReturn(List.of(link));

        TeamEventResponse response = service.getEvent(team.getId(), event.getId(), admin());

        assertThat(response.rides()).singleElement().satisfies(ride -> {
            assertThat(ride.seatsTotal()).isEqualTo(2);
            assertThat(ride.seatsTaken()).isZero();
            assertThat(ride.family()).extracting(TravelPassengerResponse::playerProfileId).containsExactly(child.getId());
        });
        assertThat(response.summary().unseatedGoingCount()).isEqualTo(1);
        assertThat(response.roster())
                .filteredOn(TravelRosterEntryResponse::playerProfileId, child.getId())
                .singleElement()
                .satisfies(entry -> assertThat(entry.parentDrives()).isTrue());
        assertThat(response.roster())
                .filteredOn(TravelRosterEntryResponse::playerProfileId, other.getId())
                .singleElement()
                .satisfies(entry -> assertThat(entry.parentDrives()).isFalse());
    }

    @Test
    void shrinkingBelowOccupantsDoesNotSave() {
        board();
        Coach coach = coachOnTeam();
        RideOffer offer = offer(coach, 2);
        PlayerProfile first = profile("Ann", "Adams");
        PlayerProfile second = profile("Bea", "Brown");
        when(rideOfferRepository.findByIdForUpdate(offer.getId())).thenReturn(Optional.of(offer));
        when(rideSeatRepository.findByOffer_Id(offer.getId())).thenReturn(List.of(seat(offer, first), seat(offer, second)));
        when(teamRosterRepository.findProfileIdsOnTeam(any(), any())).thenReturn(List.of(first.getId(), second.getId()));
        when(attendanceRepository.findByEventAndProfiles(any(), any())).thenReturn(List.of(goingRow(first, true), goingRow(second, true)));

        assertThatThrownBy(() -> service.updateRide(
                team.getId(), event.getId(), offer.getId(), rideRequest(1, "Kapu", offer.getDepartureAt(), offer.getUpdatedAt()), coach))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.travel.ride_too_small");
        verify(rideOfferRepository, never()).saveAndFlush(any());
    }

    @Test
    void deletingAnOfferKeepsGoingStatus() {
        board();
        Coach coach = coachOnTeam();
        RideOffer offer = offer(coach, 2);
        RideSeat seat = seat(offer, profile("Ann", "Adams"));
        when(rideOfferRepository.findByIdAndEvent_Id(offer.getId(), event.getId())).thenReturn(Optional.of(offer));
        when(rideOfferRepository.findByIdForUpdate(offer.getId())).thenReturn(Optional.of(offer));
        when(rideSeatRepository.findByOffer_Id(offer.getId())).thenReturn(List.of(seat));

        service.deleteRide(team.getId(), event.getId(), offer.getId(), coach);

        verify(rideOfferRepository).delete(offer);
        verify(attendanceRepository, never()).save(any());
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void videoMatchForAnotherTeamAndCupMatchWithoutRealTeamAreBadRequests() {
        board();
        Coach coach = coachOnTeam();
        Match match = new Match();
        match.setId(UUID.randomUUID());
        Team other = new Team();
        other.setId(UUID.randomUUID());
        match.setHomeTeam(other);
        when(matchRepository.findById(match.getId())).thenReturn(Optional.of(match));
        assertThatThrownBy(() -> service.createEvent(team.getId(), new UpsertTeamEventRequest(
                TeamEventType.MATCH, event.getStartsAt(), event.getMeetingAt(), null, "Kapu", null, null, match.getId(), null, null), coach))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.travel.video_match_team");

        CupMatch cupMatch = new CupMatch();
        cupMatch.setId(UUID.randomUUID());
        cupMatch.setHomeTeam(new CupTeam());
        cupMatch.setAwayTeam(new CupTeam());
        when(cupMatchRepository.findById(cupMatch.getId())).thenReturn(Optional.of(cupMatch));
        assertThatThrownBy(() -> service.createEvent(team.getId(), new UpsertTeamEventRequest(
                TeamEventType.MATCH, event.getStartsAt(), event.getMeetingAt(), null, "Kapu", null, null, null, cupMatch.getId(), null), coach))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("messageCode", "error.travel.cup_match_team");
        verify(teamEventRepository, never()).save(any());
    }

    private void board() {
        team = new Team();
        team.setId(UUID.randomUUID());
        team.setName("U12");
        Club club = new Club();
        club.setId(UUID.randomUUID());
        team.setClub(club);
        event = new TeamEvent();
        event.setId(UUID.randomUUID());
        event.setTeam(team);
        event.setType(TeamEventType.TRAINING);
        event.setStartsAt(LocalDateTime.now().plusDays(3).truncatedTo(ChronoUnit.MICROS));
        event.setMeetingAt(event.getStartsAt().minusHours(1));
        event.setMeetingPoint("Kapu");
        event.setUpdatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        lenient().when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        lenient().when(teamEventRepository.findByIdAndTeam_Id(event.getId(), team.getId())).thenReturn(Optional.of(event));
        lenient().when(teamEventRepository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
    }

    private UpsertTeamEventRequest eventBody(LocalDateTime expected) {
        return new UpsertTeamEventRequest(
                TeamEventType.TRAINING, event.getStartsAt(), event.getMeetingAt(), null, "Kapu", null, null, null, null, expected);
    }

    private UpsertRideOfferRequest rideRequest(int seats, String place, LocalDateTime at, LocalDateTime expected) {
        return new UpsertRideOfferRequest(seats, place, at, null, expected);
    }

    private RideOffer offer(User driver, int seats) {
        RideOffer offer = new RideOffer();
        offer.setId(UUID.randomUUID());
        offer.setEvent(event);
        offer.setDriver(driver);
        offer.setSeatsTotal(seats);
        offer.setDeparturePlace("Kapu");
        offer.setDepartureAt(event.getMeetingAt());
        offer.setUpdatedAt(event.getUpdatedAt());
        return offer;
    }

    private RideSeat seat(RideOffer offer, PlayerProfile profile) {
        RideSeat seat = new RideSeat();
        seat.setId(UUID.randomUUID());
        seat.setOffer(offer);
        seat.setPlayerProfile(profile);
        return seat;
    }

    private void onRoster(PlayerProfile profile) {
        when(playerProfileRepository.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(teamRosterRepository.existsById(new TeamRosterId(profile.getId(), team.getId()))).thenReturn(true);
    }

    private void going(PlayerProfile profile) {
        Attendance attendance = new Attendance();
        attendance.setStatus(AttendanceStatus.GOING);
        attendance.setWantsRide(true);
        attendance.setPlayerProfile(profile);
        when(attendanceRepository.findById(any())).thenReturn(Optional.of(attendance));
    }

    private Attendance goingRow(PlayerProfile profile, boolean wantsRide) {
        Attendance attendance = new Attendance();
        attendance.setId(new AttendanceId(event.getId(), profile.getId()));
        attendance.setPlayerProfile(profile);
        attendance.setStatus(AttendanceStatus.GOING);
        attendance.setWantsRide(wantsRide);
        return attendance;
    }

    private void link(Parent parent, PlayerProfile... children) {
        List<PlayerParent> links = java.util.Arrays.stream(children).map(child -> {
            PlayerParent link = new PlayerParent();
            link.setUser(parent);
            link.setPlayerProfile(child);
            return link;
        }).toList();
        when(playerParentRepository.findLinksForParentOnTeam(parent.getId(), team.getId())).thenReturn(links);
    }

    private Coach coachOnTeam() {
        Coach coach = new Coach();
        coach.setId(UUID.randomUUID());
        coach.setRole(UserRole.COACH);
        coach.setFirstName("Ed");
        coach.setLastName("Zo");
        lenient().when(teamMembershipQuery.isCoachOnTeam(coach.getId(), team.getId())).thenReturn(true);
        return coach;
    }

    private Parent parent() {
        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setRole(UserRole.PARENT);
        parent.setFirstName("Pa");
        parent.setLastName("Rent");
        return parent;
    }

    private Player player() {
        Player player = new Player();
        player.setId(UUID.randomUUID());
        player.setRole(UserRole.PLAYER);
        player.setFirstName("Pla");
        player.setLastName("Yer");
        return player;
    }

    private Admin admin() {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        admin.setRole(UserRole.ADMIN);
        return admin;
    }

    private ClubAdmin clubAdmin() {
        ClubAdmin clubAdmin = new ClubAdmin();
        clubAdmin.setId(UUID.randomUUID());
        clubAdmin.setRole(UserRole.CLUB_ADMIN);
        return clubAdmin;
    }

    private PlayerProfile linkedPlayer(Player player, LocalDate dateOfBirth, boolean canOfferRides) {
        PlayerProfile own = profile("Ann", "Adams");
        own.setLinkedUser(player);
        own.setDateOfBirth(dateOfBirth);
        own.setCanOfferRides(canOfferRides);
        when(playerProfileRepository.existsByLinkedUser_IdAndTeam_Id(player.getId(), team.getId())).thenReturn(true);
        when(playerProfileRepository.findByLinkedUser_IdAndTeam_Id(player.getId(), team.getId())).thenReturn(Optional.of(own));
        return own;
    }

    private PlayerProfile profile(String first, String last) {
        PlayerProfile profile = new PlayerProfile();
        profile.setId(UUID.randomUUID());
        profile.setFirstName(first);
        profile.setLastName(last);
        return profile;
    }
}
