package com.example.footballanalysis.service;

import com.example.footballanalysis.config.ClockConfig;
import com.example.footballanalysis.exception.ConflictException;
import com.example.footballanalysis.model.db.Attendance;
import com.example.footballanalysis.model.db.AttendanceStatus;
import com.example.footballanalysis.model.db.PlayerProfile;
import com.example.footballanalysis.model.db.RideOffer;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.db.TeamEvent;
import com.example.footballanalysis.model.db.TeamEventType;
import com.example.footballanalysis.model.db.TeamRoster;
import com.example.footballanalysis.model.db.user.Admin;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.BookSeatRequest;
import com.example.footballanalysis.repository.AttendanceRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import com.example.footballanalysis.repository.RideOfferRepository;
import com.example.footballanalysis.repository.RideSeatRepository;
import com.example.footballanalysis.repository.TeamEventRepository;
import com.example.footballanalysis.repository.TeamMembershipQuery;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.repository.TeamRosterRepository;
import com.example.footballanalysis.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({TeamEventService.class, TravelAccess.class, UserAccessService.class, AuditEventService.class, TeamMembershipQuery.class, ClockConfig.class})
class RideOfferLockPostgresIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired private TeamEventService teamEventService;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TeamEventRepository teamEventRepository;
    @Autowired private PlayerProfileRepository playerProfileRepository;
    @Autowired private TeamRosterRepository teamRosterRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private RideOfferRepository rideOfferRepository;
    @Autowired private RideSeatRepository rideSeatRepository;

    @Test
    void twoThreadsCannotBothTakeTheLastSeat() throws Exception {
        Admin admin = user(new Admin(), "admin");
        Parent driver = user(new Parent(), "driver");
        driver.setRole(UserRole.PARENT);
        userRepository.save(admin);
        userRepository.save(driver);

        Team team = new Team();
        team.setName("Travel lock " + UUID.randomUUID());
        teamRepository.save(team);

        PlayerProfile first = profile("Ann", "Adams");
        PlayerProfile second = profile("Bea", "Brown");
        playerProfileRepository.save(first);
        playerProfileRepository.save(second);
        roster(first, team);
        roster(second, team);

        TeamEvent event = new TeamEvent();
        event.setTeam(team);
        event.setType(TeamEventType.TRAINING);
        event.setStartsAt(LocalDateTime.now().plusDays(2));
        event.setMeetingAt(LocalDateTime.now().plusDays(2).minusHours(1));
        event.setMeetingPoint("Kapu");
        teamEventRepository.save(event);

        RideOffer offer = new RideOffer();
        offer.setEvent(event);
        offer.setDriver(driver);
        offer.setSeatsTotal(1);
        offer.setDeparturePlace("Kapu");
        offer.setDepartureAt(event.getMeetingAt());
        rideOfferRepository.save(offer);
        attendance(event, first);
        attendance(event, second);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> left = pool.submit(() -> book(start, admin, team, event, offer, first));
            Future<String> right = pool.submit(() -> book(start, admin, team, event, offer, second));
            start.countDown();
            assertThat(java.util.List.of(left.get(30, TimeUnit.SECONDS), right.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("ok", "error.travel.ride_full");
        } finally {
            pool.shutdownNow();
        }
        assertThat(rideSeatRepository.count()).isEqualTo(1);
    }

    private String book(CountDownLatch start, Admin admin, Team team, TeamEvent event, RideOffer offer, PlayerProfile profile) {
        try {
            start.await(30, TimeUnit.SECONDS);
            teamEventService.bookSeat(team.getId(), event.getId(), offer.getId(), new BookSeatRequest(profile.getId()), admin);
            return "ok";
        } catch (ConflictException ex) {
            return ex.getMessageCode();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
    }

    private static <T extends com.example.footballanalysis.model.db.user.User> T user(T user, String name) {
        user.setId(UUID.randomUUID());
        user.setEmail(name + "-" + user.getId() + "@test.test");
        user.setFirstName(name);
        user.setLastName("Test");
        user.setRole(UserRole.ADMIN);
        return user;
    }

    private static PlayerProfile profile(String first, String last) {
        PlayerProfile profile = new PlayerProfile();
        profile.setFirstName(first);
        profile.setLastName(last);
        return profile;
    }

    private void roster(PlayerProfile profile, Team team) {
        TeamRoster row = new TeamRoster();
        row.setId(new com.example.footballanalysis.model.db.TeamRosterId(profile.getId(), team.getId()));
        row.setPlayerProfile(profile);
        row.setTeam(team);
        teamRosterRepository.save(row);
    }

    private void attendance(TeamEvent event, PlayerProfile profile) {
        Attendance attendance = new Attendance();
        attendance.setId(new com.example.footballanalysis.model.db.AttendanceId(event.getId(), profile.getId()));
        attendance.setWantsRide(true);
        attendance.setEvent(event);
        attendance.setPlayerProfile(profile);
        attendance.setStatus(AttendanceStatus.GOING);
        attendanceRepository.save(attendance);
    }
}
