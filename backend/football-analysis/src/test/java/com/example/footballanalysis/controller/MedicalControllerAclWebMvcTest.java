package com.example.footballanalysis.controller;

import com.example.footballanalysis.config.SecurityConfig;
import com.example.footballanalysis.model.db.user.Parent;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.ClubMedicalNoticeResponse;
import com.example.footballanalysis.model.responses.ClubMedicalSettingsResponse;
import com.example.footballanalysis.model.responses.ClubMedicalStatusResponse;
import com.example.footballanalysis.model.responses.MedicalAdminAccessResponse;
import com.example.footballanalysis.model.responses.MedicalFileResponse;
import com.example.footballanalysis.model.responses.MedicalStatusResponse;
import com.example.footballanalysis.model.responses.MedicalUploadResponse;
import com.example.footballanalysis.model.responses.MedicalUploadRightResponse;
import com.example.footballanalysis.model.responses.PageResponse;
import com.example.footballanalysis.model.responses.TeamMedicalStatusResponse;
import com.example.footballanalysis.service.MedicalCertificateService;
import com.example.footballanalysis.service.MedicalSettingsService;
import com.example.footballanalysis.service.UserAccessService;
import com.example.footballanalysis.service.UserRoleSyncService;
import com.example.footballanalysis.util.KeycloakRoleConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The route filter in front of the sport medical endpoints. The services decide the rest. */
@WebMvcTest(controllers = MedicalController.class)
@Import({SecurityConfig.class, KeycloakRoleConverter.class, MedicalControllerAclWebMvcTest.MockConfig.class})
class MedicalControllerAclWebMvcTest {

    @TestConfiguration
    static class MockConfig {
        @Bean MedicalCertificateService medicalCertificateService() { return Mockito.mock(MedicalCertificateService.class); }
        @Bean MedicalSettingsService medicalSettingsService() { return Mockito.mock(MedicalSettingsService.class); }
        @Bean UserAccessService userAccessService() { return Mockito.mock(UserAccessService.class); }
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
    @Autowired MedicalCertificateService medicalCertificateService;
    @Autowired MedicalSettingsService medicalSettingsService;
    @Autowired UserAccessService userAccessService;

    private final UUID profileId = UUID.randomUUID();
    private final UUID certificateId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();
    private final UUID clubId = UUID.randomUUID();

    @BeforeEach
    void stubServices() {
        clearInvocations(medicalCertificateService, medicalSettingsService);
        Parent caller = new Parent();
        caller.setId(UUID.randomUUID());
        caller.setRole(UserRole.PARENT);
        given(userAccessService.resolveCurrentUser(any())).willReturn(caller);
        given(medicalCertificateService.submit(any(), any(), any()))
                .willReturn(new MedicalUploadResponse(certificateId, "http://minio/put", 600L, false));
        given(medicalCertificateService.complete(any(), any(), any())).willReturn(MedicalStatusResponse.notRequired());
        given(medicalCertificateService.openFile(any(), any(), any())).willReturn(new MedicalFileResponse("http://minio/get", 120));
        given(medicalCertificateService.getStatus(any(), any())).willReturn(MedicalStatusResponse.notRequired());
        given(medicalCertificateService.getMyStatus(any())).willReturn(MedicalStatusResponse.notRequired());
        given(medicalCertificateService.setUploadRight(any(), any(), any())).willReturn(new MedicalUploadRightResponse(true));
        given(medicalCertificateService.teamStatus(any(), any())).willReturn(new TeamMedicalStatusResponse(true, "INHERIT", "INHERIT", List.of()));
        given(medicalSettingsService.updateTeamMode(any(), any(), any())).willReturn(new TeamMedicalStatusResponse(false, "OFF", "INHERIT", List.of()));
        given(medicalSettingsService.getClubSettings(any(), any())).willReturn(new ClubMedicalSettingsResponse(clubId, "OFF", null, 3, "DATE_ONLY", null, null, null, null, null, null, null, false, List.of(), true, List.of(), false, null));
        given(medicalSettingsService.updateClubSettings(any(), any(), any())).willReturn(new ClubMedicalSettingsResponse(clubId, "ON", null, 3, "DATE_ONLY", null, null, null, null, null, null, null, false, List.of(), true, List.of(), false, null));
        given(medicalCertificateService.adminAccess(any(), any(), any()))
                .willReturn(new MedicalAdminAccessResponse(false, false, null, false, null, null, null, null, false, 0, 0, null, null));
        given(medicalCertificateService.verify(any(), any(), any())).willReturn(MedicalStatusResponse.notRequired());
        given(medicalCertificateService.reject(any(), any(), any())).willReturn(MedicalStatusResponse.notRequired());
        given(medicalCertificateService.correctDate(any(), any(), any())).willReturn(MedicalStatusResponse.notRequired());
        given(medicalCertificateService.setParentAccess(any(), any())).willReturn(new MedicalUploadRightResponse(true));
        given(medicalCertificateService.submitInPerson(any(), any(), any())).willReturn(MedicalStatusResponse.notRequired());
        given(medicalCertificateService.clubStatus(any(), any(), any(), any(), any(), any()))
                .willReturn(new ClubMedicalStatusResponse(List.of(), List.of(), Map.of(), new PageResponse<>(List.of(), 0, 20, 0, 0)));
        given(medicalSettingsService.getNotice(any(), any())).willReturn(new ClubMedicalNoticeResponse("URL", "https://club.example/notice", null));
        given(medicalSettingsService.uploadNotice(any(), any(), any())).willReturn(new ClubMedicalSettingsResponse(clubId, "OFF", null, 3, "DATE_ONLY", null, null, null, null, null, null, null, false, List.of(), true, List.of(), false, null));
        given(medicalCertificateService.clubAdminAccesses(any(), any(), any(), any()))
                .willReturn(new PageResponse<>(List.of(), 1, 20, 0, 0));
    }

    @Test
    void fanIsStoppedOnEveryMedicalRoute() throws Exception {
        for (MockHttpServletRequestBuilder request : allRoutes()) {
            mockMvc.perform(request.with(jwtWith("fan"))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(medicalCertificateService, medicalSettingsService);
    }

    @Test
    void coachReadsDatesAndUploadsButCannotWithdrawOrTouchSettings() throws Exception {
        mockMvc.perform(get("/api/player-profiles/{id}/medical", profileId).with(jwtWith("coach"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/teams/{id}/medical-status", teamId).with(jwtWith("coach"))).andExpect(status().isOk());
        // The file route lets the coach through; the service opens it only where the club allows coaches.
        mockMvc.perform(openFile().with(jwtWith("coach"))).andExpect(status().isOk());

        // The upload routes let the coach through; the service accepts it only where the file is asked for.
        mockMvc.perform(submit().with(jwtWith("coach"))).andExpect(status().is2xxSuccessful());
        mockMvc.perform(complete().with(jwtWith("coach"))).andExpect(status().is2xxSuccessful());

        for (MockHttpServletRequestBuilder request : List.of(
                withdraw(), uploadRight(), teamMode(), clubSettingsGet(), clubSettingsPut())) {
            mockMvc.perform(request.with(jwtWith("coach"))).andExpect(status().isForbidden());
        }
    }

    @Test
    void parentReachesSubmitCompleteOpenWithdrawAndTheUploadRight() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                submit(), complete(), openFile(), withdraw(), uploadRight(),
                get("/api/player-profiles/{id}/medical", profileId))) {
            mockMvc.perform(request.with(jwtWith("parent"))).andExpect(status().is2xxSuccessful());
        }
        mockMvc.perform(get("/api/teams/{id}/medical-status", teamId).with(jwtWith("parent"))).andExpect(status().isForbidden());
        mockMvc.perform(clubSettingsPut().with(jwtWith("parent"))).andExpect(status().isForbidden());
    }

    @Test
    void playerReachesSubmitOpenWithdrawAndOwnStatusButNotRightOrSummary() throws Exception {
        // The route lets the player withdraw; the service allows it only on an adult's own profile.
        for (MockHttpServletRequestBuilder request : List.of(
                submit(), complete(), openFile(), withdraw(), get("/api/player-profiles/{id}/medical", profileId), get("/api/me/medical"))) {
            mockMvc.perform(request.with(jwtWith("player"))).andExpect(status().is2xxSuccessful());
        }
        for (MockHttpServletRequestBuilder request : List.of(
                uploadRight(), get("/api/teams/{id}/medical-status", teamId), teamMode(), clubSettingsPut())) {
            mockMvc.perform(request.with(jwtWith("player"))).andExpect(status().isForbidden());
        }
    }

    @Test
    void clubAdminReachesEveryManagementRoute() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                submit(), complete(), openFile(), withdraw(), uploadRight(), teamMode(), clubSettingsGet(), clubSettingsPut(),
                get("/api/player-profiles/{id}/medical", profileId), get("/api/teams/{id}/medical-status", teamId))) {
            mockMvc.perform(request.with(jwtWith("club_admin"))).andExpect(status().is2xxSuccessful());
        }
        mockMvc.perform(get("/api/me/medical").with(jwtWith("club_admin"))).andExpect(status().isForbidden());
        mockMvc.perform(adminAccessesList().with(jwtWith("club_admin"))).andExpect(status().isOk());
        mockMvc.perform(adminAccess().with(jwtWith("club_admin"))).andExpect(status().isForbidden());
        mockMvc.perform(adminWithdrawal().with(jwtWith("club_admin"))).andExpect(status().isForbidden());
    }

    @Test
    void platformAdminOnlyReachesBreakGlassAndReadsTheClubSettings() throws Exception {
        mockMvc.perform(adminAccess().with(jwtWith("admin"))).andExpect(status().isOk());
        mockMvc.perform(adminWithdrawal().with(jwtWith("admin"))).andExpect(status().isNoContent());
        mockMvc.perform(clubSettingsGet().with(jwtWith("admin"))).andExpect(status().isOk());

        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/player-profiles/{id}/medical", profileId), get("/api/teams/{id}/medical-status", teamId),
                teamMode(), clubSettingsPut(), adminAccessesList())) {
            mockMvc.perform(request.with(jwtWith("admin"))).andExpect(status().isForbidden());
        }
    }

    @Test
    void everySignedInRoleReadsTheNoticeButOnlyTheClubAdminChangesIt() throws Exception {
        for (String role : List.of("admin", "club_admin", "coach", "player", "parent", "fan")) {
            mockMvc.perform(get("/api/clubs/{id}/medical-notice", clubId).with(jwtWith(role))).andExpect(status().isOk());
        }
        mockMvc.perform(noticeUpload().with(jwtWith("club_admin"))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/clubs/{id}/medical-notice", clubId).with(jwtWith("club_admin"))).andExpect(status().isNoContent());
        for (String role : List.of("admin", "coach", "player", "parent", "fan")) {
            mockMvc.perform(noticeUpload().with(jwtWith(role))).andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/clubs/{id}/medical-notice", clubId).with(jwtWith(role))).andExpect(status().isForbidden());
        }
    }

    private MockHttpServletRequestBuilder noticeUpload() {
        MockMultipartFile file = new MockMultipartFile("file", "notice.pdf", "application/pdf", "%PDF-1.7".getBytes());
        return multipart(HttpMethod.PUT, "/api/clubs/{id}/medical-notice", clubId).file(file);
    }

    @Test
    void onlyTheClubAdminAndTheCoachVerifyAndEnterInPerson() throws Exception {
        for (String role : List.of("club_admin", "coach")) {
            mockMvc.perform(verification().with(jwtWith(role))).andExpect(status().isOk());
            mockMvc.perform(delete("/api/player-profiles/{id}/medical/verification", profileId).with(jwtWith(role)))
                    .andExpect(status().isNoContent());
            mockMvc.perform(inPerson().with(jwtWith(role))).andExpect(status().isOk());
            mockMvc.perform(rejection().with(jwtWith(role))).andExpect(status().isOk());
            mockMvc.perform(dateCorrection().with(jwtWith(role))).andExpect(status().isOk());
        }
        for (String role : List.of("admin", "parent", "player", "fan")) {
            mockMvc.perform(rejection().with(jwtWith(role))).andExpect(status().isForbidden());
            mockMvc.perform(dateCorrection().with(jwtWith(role))).andExpect(status().isForbidden());
            mockMvc.perform(verification().with(jwtWith(role))).andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/player-profiles/{id}/medical/verification", profileId).with(jwtWith(role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(inPerson().with(jwtWith(role))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/clubs/{id}/medical-status", clubId).with(jwtWith("club_admin"))).andExpect(status().isOk());
        for (String role : List.of("admin", "coach", "parent", "player")) {
            mockMvc.perform(get("/api/clubs/{id}/medical-status", clubId).with(jwtWith(role))).andExpect(status().isForbidden());
        }
    }

    @Test
    void onlyTheParentAcknowledgesAndOnlyThePlayerSetsTheParentAccess() throws Exception {
        String seen = "{\"certificateId\":\"" + certificateId + "\"}";
        mockMvc.perform(post("/api/player-profiles/{id}/medical/seen", profileId).contentType(APPLICATION_JSON).content(seen)
                .with(jwtWith("parent"))).andExpect(status().isNoContent());
        mockMvc.perform(put("/api/me/medical/parent-access").contentType(APPLICATION_JSON).content("{\"allowed\":true}")
                .with(jwtWith("player"))).andExpect(status().isOk());
        for (String role : List.of("admin", "club_admin", "coach", "player", "fan")) {
            mockMvc.perform(post("/api/player-profiles/{id}/medical/seen", profileId).contentType(APPLICATION_JSON).content(seen)
                    .with(jwtWith(role))).andExpect(status().isForbidden());
        }
        for (String role : List.of("admin", "club_admin", "coach", "parent", "fan")) {
            mockMvc.perform(put("/api/me/medical/parent-access").contentType(APPLICATION_JSON).content("{\"allowed\":true}")
                    .with(jwtWith(role))).andExpect(status().isForbidden());
        }
    }

    private MockHttpServletRequestBuilder rejection() {
        return post("/api/player-profiles/{id}/medical/rejection", profileId)
                .contentType(APPLICATION_JSON).content("{\"result\":\"VERSENYEZHET\",\"validUntil\":\"2027-05-03\",\"method\":\"FILE\",\"reason\":\"ILLEGIBLE\"}");
    }

    private MockHttpServletRequestBuilder dateCorrection() {
        return post("/api/player-profiles/{id}/medical/date-correction", profileId)
                .contentType(APPLICATION_JSON).content("{\"result\":\"VERSENYEZHET\",\"validUntil\":\"2027-05-03\",\"method\":\"FILE\",\"correctedValidUntil\":\"2027-06-03\"}");
    }

    private MockHttpServletRequestBuilder verification() {
        return post("/api/player-profiles/{id}/medical/verification", profileId)
                .contentType(APPLICATION_JSON).content("{\"result\":\"VERSENYEZHET\",\"validUntil\":\"2027-05-03\",\"method\":\"ORIGINAL\"}");
    }

    private MockHttpServletRequestBuilder inPerson() {
        return post("/api/player-profiles/{id}/medical/in-person", profileId)
                .contentType(APPLICATION_JSON).content("{\"result\":\"ALKALMAS\",\"validUntil\":\"2027-05-03\",\"seenOriginal\":true}");
    }

    @Test
    void coachParentAndPlayerCannotUseBreakGlass() throws Exception {
        for (String role : List.of("coach", "parent", "player")) {
            mockMvc.perform(adminAccess().with(jwtWith(role))).andExpect(status().isForbidden());
            mockMvc.perform(adminWithdrawal().with(jwtWith(role))).andExpect(status().isForbidden());
            mockMvc.perform(adminAccessesList().with(jwtWith(role))).andExpect(status().isForbidden());
        }
    }

    private List<MockHttpServletRequestBuilder> allRoutes() {
        return List.of(submit(), complete(), openFile(), withdraw(), uploadRight(), teamMode(), clubSettingsGet(), clubSettingsPut(),
                get("/api/player-profiles/{id}/medical", profileId), get("/api/me/medical"),
                get("/api/teams/{id}/medical-status", teamId), adminAccess(), adminWithdrawal(), adminAccessesList());
    }

    private MockHttpServletRequestBuilder submit() {
        return post("/api/player-profiles/{id}/medical-certificates", profileId)
                .contentType(APPLICATION_JSON)
                .content("{\"validUntil\":\"2027-05-03\",\"acknowledged\":true,\"contentType\":\"application/pdf\",\"sizeBytes\":1000}");
    }

    private MockHttpServletRequestBuilder complete() {
        return post("/api/player-profiles/{id}/medical-certificates/{cid}/complete", profileId, certificateId);
    }

    private MockHttpServletRequestBuilder openFile() {
        return get("/api/player-profiles/{id}/medical-certificates/{cid}/file", profileId, certificateId);
    }

    private MockHttpServletRequestBuilder withdraw() {
        return delete("/api/player-profiles/{id}/medical-certificates/{cid}", profileId, certificateId);
    }

    private MockHttpServletRequestBuilder uploadRight() {
        return put("/api/player-profiles/{id}/medical-upload-right", profileId)
                .contentType(APPLICATION_JSON).content("{\"allowed\":true}");
    }

    private MockHttpServletRequestBuilder teamMode() {
        return put("/api/teams/{id}/medical-mode", teamId)
                .contentType(APPLICATION_JSON).content("{\"mode\":\"OFF\",\"confirmDeletion\":true}");
    }

    private MockHttpServletRequestBuilder adminAccess() {
        return post("/api/player-profiles/{id}/medical/admin-access", profileId)
                .contentType(APPLICATION_JSON).content("{\"reason\":\"The club asked for a check of the upload state.\"}");
    }

    private MockHttpServletRequestBuilder adminWithdrawal() {
        return post("/api/player-profiles/{id}/medical/admin-withdrawal", profileId)
                .contentType(APPLICATION_JSON)
                .content("{\"certificateId\":\"" + certificateId + "\",\"reason\":\"The club asked to delete this certificate.\"}");
    }

    private MockHttpServletRequestBuilder adminAccessesList() {
        return get("/api/clubs/{id}/medical-admin-accesses", clubId);
    }

    private MockHttpServletRequestBuilder clubSettingsGet() {
        return get("/api/clubs/{id}/medical-settings", clubId);
    }

    private MockHttpServletRequestBuilder clubSettingsPut() {
        return put("/api/clubs/{id}/medical-settings", clubId)
                .contentType(APPLICATION_JSON).content("{\"mode\":\"ON\",\"acknowledged\":true}");
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
