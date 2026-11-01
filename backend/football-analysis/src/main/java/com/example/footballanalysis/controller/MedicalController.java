package com.example.footballanalysis.controller;

import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.requests.MedicalAdminAccessRequest;
import com.example.footballanalysis.model.requests.MedicalAdminWithdrawalRequest;
import com.example.footballanalysis.model.requests.MedicalInPersonRequest;
import com.example.footballanalysis.model.requests.MedicalSeenRequest;
import com.example.footballanalysis.model.requests.MedicalDateCorrectionRequest;
import com.example.footballanalysis.model.requests.MedicalRejectionRequest;
import com.example.footballanalysis.model.requests.MedicalVerificationRequest;
import com.example.footballanalysis.model.requests.SetMedicalUploadRightRequest;
import com.example.footballanalysis.model.requests.SubmitMedicalCertificateRequest;
import com.example.footballanalysis.model.requests.UpdateClubMedicalSettingsRequest;
import com.example.footballanalysis.model.requests.UpdateTeamMedicalModeRequest;
import com.example.footballanalysis.model.responses.ClubMedicalNoticeResponse;
import com.example.footballanalysis.model.responses.ClubMedicalSettingsResponse;
import com.example.footballanalysis.model.responses.ClubMedicalStatusResponse;
import com.example.footballanalysis.model.responses.MedicalAdminAccessItem;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Sport medical certificates, the child's upload right, the club and team switches, and the platform
 * admin's break-glass access with the club's list of it.
 */
@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class MedicalController {

    private final MedicalCertificateService medicalCertificateService;
    private final MedicalSettingsService medicalSettingsService;
    private final UserAccessService userAccessService;

    @PostMapping(value = "/player-profiles/{id}/medical-certificates", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MedicalUploadResponse submit(
            @PathVariable UUID id,
            @RequestBody SubmitMedicalCertificateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return medicalCertificateService.submit(id, request, actor(jwt));
    }

    @PostMapping("/player-profiles/{id}/medical-certificates/{certificateId}/complete")
    public MedicalStatusResponse complete(
            @PathVariable UUID id,
            @PathVariable UUID certificateId,
            @AuthenticationPrincipal Jwt jwt) {
        return medicalCertificateService.complete(id, certificateId, actor(jwt));
    }

    @GetMapping("/player-profiles/{id}/medical-certificates/{certificateId}/file")
    public MedicalFileResponse openFile(
            @PathVariable UUID id,
            @PathVariable UUID certificateId,
            @AuthenticationPrincipal Jwt jwt) {
        return medicalCertificateService.openFile(id, certificateId, actor(jwt));
    }

    @DeleteMapping("/player-profiles/{id}/medical-certificates/{certificateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(
            @PathVariable UUID id,
            @PathVariable UUID certificateId,
            @AuthenticationPrincipal Jwt jwt) {
        medicalCertificateService.withdraw(id, certificateId, actor(jwt));
    }

    @PostMapping(value = "/player-profiles/{id}/medical/verification", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MedicalStatusResponse verify(
            @PathVariable UUID id,
            @RequestBody MedicalVerificationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return medicalCertificateService.verify(id, request, actor(jwt));
    }

    @PostMapping(value = "/player-profiles/{id}/medical/rejection", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MedicalStatusResponse reject(
            @PathVariable UUID id,
            @RequestBody MedicalRejectionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return medicalCertificateService.reject(id, request, actor(jwt));
    }

    @PostMapping(value = "/player-profiles/{id}/medical/date-correction", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MedicalStatusResponse correctDate(
            @PathVariable UUID id,
            @RequestBody MedicalDateCorrectionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return medicalCertificateService.correctDate(id, request, actor(jwt));
    }

    @DeleteMapping("/player-profiles/{id}/medical/verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unverify(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        medicalCertificateService.unverify(id, actor(jwt));
    }

    @PostMapping(value = "/player-profiles/{id}/medical/in-person", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MedicalStatusResponse submitInPerson(
            @PathVariable UUID id,
            @RequestBody MedicalInPersonRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return medicalCertificateService.submitInPerson(id, request, actor(jwt));
    }

    /** The club tab's list, paged; {@code filter} is UNVERIFIED, EXPIRING, NOT_COMPETING, BANNED, RESULT_MISSING or MISSING. */
    @GetMapping("/clubs/{clubId}/medical-status")
    public ClubMedicalStatusResponse clubStatus(
            @PathVariable UUID clubId,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "teamId", required = false) UUID teamId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @AuthenticationPrincipal Jwt jwt) {
        return medicalCertificateService.clubStatus(clubId, filter, teamId, page, size, actor(jwt));
    }

    @GetMapping("/player-profiles/{id}/medical")
    public MedicalStatusResponse getStatus(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return medicalCertificateService.getStatus(id, actor(jwt));
    }

    @PutMapping(value = "/player-profiles/{id}/medical-upload-right", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MedicalUploadRightResponse setUploadRight(
            @PathVariable UUID id,
            @Valid @RequestBody SetMedicalUploadRightRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return medicalCertificateService.setUploadRight(id, request, actor(jwt));
    }

    /** The parent saw the current certificate on the child's profile; clears the unread mark. */
    @PostMapping(value = "/player-profiles/{id}/medical/seen", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markSeen(@PathVariable UUID id, @RequestBody MedicalSeenRequest request, @AuthenticationPrincipal Jwt jwt) {
        medicalCertificateService.markSeen(id, request != null ? request.certificateId() : null, actor(jwt));
    }

    /** An adult player lets the parents see its sport medical data. */
    @PutMapping(value = "/me/medical/parent-access", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MedicalUploadRightResponse setParentAccess(
            @Valid @RequestBody SetMedicalUploadRightRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return medicalCertificateService.setParentAccess(request, actor(jwt));
    }

    @GetMapping("/me/medical")
    public MedicalStatusResponse getMyStatus(@AuthenticationPrincipal Jwt jwt) {
        return medicalCertificateService.getMyStatus(actor(jwt));
    }

    @GetMapping("/teams/{teamId}/medical-status")
    public TeamMedicalStatusResponse teamStatus(@PathVariable UUID teamId, @AuthenticationPrincipal Jwt jwt) {
        return medicalCertificateService.teamStatus(teamId, actor(jwt));
    }

    @PutMapping(value = "/teams/{teamId}/medical-mode", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TeamMedicalStatusResponse setTeamMode(
            @PathVariable UUID teamId,
            @RequestBody UpdateTeamMedicalModeRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return medicalSettingsService.updateTeamMode(teamId, request, actor(jwt));
    }

    @GetMapping("/clubs/{clubId}/medical-settings")
    public ClubMedicalSettingsResponse getClubSettings(@PathVariable UUID clubId, @AuthenticationPrincipal Jwt jwt) {
        return medicalSettingsService.getClubSettings(clubId, actor(jwt));
    }

    @PutMapping(value = "/clubs/{clubId}/medical-settings", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ClubMedicalSettingsResponse updateClubSettings(
            @PathVariable UUID clubId,
            @RequestBody UpdateClubMedicalSettingsRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return medicalSettingsService.updateClubSettings(clubId, request, actor(jwt));
    }

    /** The club's privacy notice as a PDF, at most 5 MiB. It is not health data, so it goes through the server. */
    @PutMapping(value = "/clubs/{clubId}/medical-notice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ClubMedicalSettingsResponse uploadClubNotice(
            @PathVariable UUID clubId,
            @RequestPart(name = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        return medicalSettingsService.uploadNotice(clubId, file == null ? null : file.getBytes(), actor(jwt));
    }

    @DeleteMapping("/clubs/{clubId}/medical-notice")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeClubNotice(@PathVariable UUID clubId, @AuthenticationPrincipal Jwt jwt) {
        medicalSettingsService.removeNotice(clubId, actor(jwt));
    }

    @GetMapping("/clubs/{clubId}/medical-notice")
    public ClubMedicalNoticeResponse getClubNotice(@PathVariable UUID clubId, @AuthenticationPrincipal Jwt jwt) {
        return medicalSettingsService.getNotice(clubId, actor(jwt));
    }

    @PostMapping(value = "/player-profiles/{id}/medical/admin-access", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MedicalAdminAccessResponse adminAccess(
            @PathVariable UUID id,
            @RequestBody MedicalAdminAccessRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return medicalCertificateService.adminAccess(id, request, actor(jwt));
    }

    @PostMapping(value = "/player-profiles/{id}/medical/admin-withdrawal", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adminWithdraw(
            @PathVariable UUID id,
            @RequestBody MedicalAdminWithdrawalRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        medicalCertificateService.adminWithdraw(id, request, actor(jwt));
    }

    @GetMapping("/clubs/{clubId}/medical-admin-accesses")
    public PageResponse<MedicalAdminAccessItem> clubAdminAccesses(
            @PathVariable UUID clubId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @AuthenticationPrincipal Jwt jwt) {
        return medicalCertificateService.clubAdminAccesses(clubId, page, size, actor(jwt));
    }

    private User actor(Jwt jwt) {
        return userAccessService.resolveCurrentUser(jwt);
    }
}
