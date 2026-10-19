package com.example.footballanalysis.controller;

import com.example.footballanalysis.config.SecurityConfig;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.model.db.user.ClubAdmin;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.Player;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.ChildResponse;
import com.example.footballanalysis.model.responses.TeamEventResponse;
import com.example.footballanalysis.service.ParentInviteService;
import com.example.footballanalysis.service.PlayerParentService;
import com.example.footballanalysis.service.TeamEventService;
import com.example.footballanalysis.service.TeamInviteService;
import com.example.footballanalysis.service.UserAccessService;
import com.example.footballanalysis.service.UserRegistrationService;
import com.example.footballanalysis.service.UserRoleSyncService;
import com.example.footballanalysis.util.KeycloakRoleConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {TeamEventController.class, PlayerProfileController.class})
@Import({SecurityConfig.class, KeycloakRoleConverter.class, TeamEventControllerAclWebMvcTest.MockConfig.class})
class TeamEventControllerAclWebMvcTest {

    @TestConfiguration
    static class MockConfig {
        @Bean TeamEventService teamEventService() { return Mockito.mock(TeamEventService.class); }
        @Bean UserAccessService userAccessService() { return Mockito.mock(UserAccessService.class); }
        @Bean TeamInviteService teamInviteService() { return Mockito.mock(TeamInviteService.class); }
        @Bean UserRegistrationService userRegistrationService() { return Mockito.mock(UserRegistrationService.class); }
        @Bean PlayerParentService playerParentService() { return Mockito.mock(PlayerParentService.class); }
        @Bean ParentInviteService parentInviteService() { return Mockito.mock(ParentInviteService.class); }
        @Bean UserRoleSyncService userRoleSyncService() {
            UserRoleSyncService mock = Mockito.mock(UserRoleSyncService.class);
            given(mock.localUserExists(any())).willReturn(true);
            return mock;
        }
        @Bean LocaleResolver localeResolver() { return new FixedLocaleResolver(Locale.ENGLISH); }
    }

    @BeforeAll
    static void forceEnglishLocale() {
        Locale.setDefault(Locale.ENGLISH);
    }

    @Autowired MockMvc mockMvc;
    @Autowired TeamEventService teamEventService;
    @Autowired UserAccessService userAccessService;
    @Autowired PlayerParentService playerParentService;

    @Test
    void fanCannotReadTravelAndTheServiceIsNotCalled() throws Exception {
        mockMvc.perform(get("/api/teams/{teamId}/events", UUID.randomUUID()).with(jwtWith("fan")))
                .andExpect(status().isForbidden());
        verify(teamEventService, never()).listEvents(any(), any());
    }

    @Test
    void clubAdminReachesEventReadAndCreate() throws Exception {
        UUID teamId = UUID.randomUUID();
        ClubAdmin clubAdmin = new ClubAdmin();
        clubAdmin.setId(UUID.randomUUID());
        clubAdmin.setRole(UserRole.CLUB_ADMIN);
        given(userAccessService.resolveCurrentUser(any())).willReturn(clubAdmin);
        given(teamEventService.listEvents(eq(teamId), eq(clubAdmin))).willReturn(List.of());
        given(teamEventService.createEvent(eq(teamId), any(), eq(clubAdmin))).willReturn(emptyEvent(teamId));

        mockMvc.perform(get("/api/teams/{teamId}/events", teamId).with(jwtWith("club_admin")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/teams/{teamId}/events", teamId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"type":"TRAINING","startsAt":"2026-11-01T18:00:00","meetingAt":"2026-11-01T17:00:00","meetingPoint":"Kapu"}
                                """)
                        .with(jwtWith("club_admin")))
                .andExpect(status().isOk());
        verify(teamEventService).listEvents(teamId, clubAdmin);
        verify(teamEventService).createEvent(eq(teamId), any(), eq(clubAdmin));
    }

    @Test
    void coachCannotBookSeatsButClubAdminReachesBookAndRelease() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        String body = "{\"playerProfileId\":\"" + profileId + "\"}";
        ClubAdmin clubAdmin = new ClubAdmin();
        clubAdmin.setId(UUID.randomUUID());
        clubAdmin.setRole(UserRole.CLUB_ADMIN);

        mockMvc.perform(post("/api/teams/{teamId}/events/{eventId}/rides/{offerId}/seats", teamId, eventId, offerId)
                        .contentType(APPLICATION_JSON)
                        .content(body)
                        .with(jwtWith("coach")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/teams/{teamId}/events/{eventId}/rides/{offerId}/seats/{profileId}", teamId, eventId, offerId, profileId)
                        .with(jwtWith("coach")))
                .andExpect(status().isForbidden());
        verify(teamEventService, never()).bookSeat(any(), any(), any(), any(), any());
        verify(teamEventService, never()).releaseSeat(any(), any(), any(), any(), any());

        given(userAccessService.resolveCurrentUser(any())).willReturn(clubAdmin);
        given(teamEventService.bookSeat(eq(teamId), eq(eventId), eq(offerId), any(), eq(clubAdmin))).willReturn(emptyEvent(teamId));
        mockMvc.perform(post("/api/teams/{teamId}/events/{eventId}/rides/{offerId}/seats", teamId, eventId, offerId)
                        .contentType(APPLICATION_JSON)
                        .content(body)
                        .with(jwtWith("club_admin")))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/teams/{teamId}/events/{eventId}/rides/{offerId}/seats/{profileId}", teamId, eventId, offerId, profileId)
                        .with(jwtWith("club_admin")))
                .andExpect(status().isNoContent());
        verify(teamEventService).bookSeat(eq(teamId), eq(eventId), eq(offerId), any(), eq(clubAdmin));
        verify(teamEventService).releaseSeat(teamId, eventId, offerId, profileId, clubAdmin);
    }

    @Test
    void playerReachesRideCreateAndTheServiceCanRejectTheFlag() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Player player = new Player();
        player.setId(UUID.randomUUID());
        player.setRole(UserRole.PLAYER);
        given(userAccessService.resolveCurrentUser(any())).willReturn(player);
        given(teamEventService.createRide(eq(teamId), eq(eventId), any(), eq(player)))
                .willThrow(new ForbiddenException("error.travel.ride_not_allowed", new Object[0], "This player is not allowed to offer rides."));

        mockMvc.perform(post("/api/teams/{teamId}/events/{eventId}/rides", teamId, eventId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"seatsTotal\":2}")
                        .with(jwtWith("player")))
                .andExpect(status().isForbidden());
        verify(teamEventService).createRide(eq(teamId), eq(eventId), any(), eq(player));
    }

    @Test
    void onlyAParentReachesRideOfferRight() throws Exception {
        UUID profileId = UUID.randomUUID();
        String body = "{\"allowed\":true}";
        mockMvc.perform(put("/api/player-profiles/{id}/ride-offer-right", profileId)
                        .contentType(APPLICATION_JSON).content(body).with(jwtWith("player")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/player-profiles/{id}/ride-offer-right", profileId)
                        .contentType(APPLICATION_JSON).content(body).with(jwtWith("coach")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/player-profiles/{id}/ride-offer-right", profileId)
                        .contentType(APPLICATION_JSON).content(body).with(jwtWith("club_admin")))
                .andExpect(status().isForbidden());
        verify(playerParentService, never()).setRideOfferRight(any(), any(), any());

        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setRole(UserRole.PARENT);
        given(userAccessService.resolveCurrentUser(any())).willReturn(parent);
        given(playerParentService.setRideOfferRight(eq(profileId), any(), eq(parent)))
                .willReturn(new ChildResponse(profileId, "Ann", "Adams", null, List.of(), true));
        mockMvc.perform(put("/api/player-profiles/{id}/ride-offer-right", profileId)
                        .contentType(APPLICATION_JSON).content(body).with(jwtWith("parent")))
                .andExpect(status().isOk());
        verify(playerParentService).setRideOfferRight(eq(profileId), any(), eq(parent));

        given(playerParentService.setRideOfferRight(eq(profileId), any(), eq(parent)))
                .willThrow(new ForbiddenException("error.auth.forbidden", new Object[0], "Access denied."));
        mockMvc.perform(put("/api/player-profiles/{id}/ride-offer-right", profileId)
                        .contentType(APPLICATION_JSON).content(body).with(jwtWith("club_admin", "parent")))
                .andExpect(status().isForbidden());
    }

    private static TeamEventResponse emptyEvent(UUID teamId) {
        return new TeamEventResponse(
                UUID.randomUUID(), teamId, null, null, null, null, null, null, null, null, null, null,
                null, false, List.of(), List.of(), null);
    }

    private static RequestPostProcessor jwtWith(String... roles) {
        return jwt().jwt(token -> token
                        .claim("azp", "football-web-client")
                        .claim("email", roles[0] + "@test.com")
                        .claim("resource_access", Map.of(
                                "football-web-client", Map.of("roles", List.of(roles)))))
                .authorities(java.util.Arrays.stream(roles)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
