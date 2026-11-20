package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.Club;
import com.example.footballanalysis.model.db.MedicalCertificate;
import com.example.footballanalysis.model.db.user.User;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * For the service tests that mock {@link MedicalAccessService} and {@link MedicalRequirementService}: the child has one
 * requiring club that is ready, every row is meant for it, and so the club-bound checks
 * (docs/funkciok/sportorvosi/sportorvosi-klubhoz-kotes.md) answer as the older, club-blind checks the tests stub. A test
 * about the binding itself stubs the club-bound methods directly.
 */
final class MedicalClubBindingStubs {

    private MedicalClubBindingStubs() {
    }

    /** A ready club with this id, name and notice, as the recipients list returns it. */
    static Club readyClub(UUID id, String name) {
        Club club = new Club();
        club.setId(id);
        club.setName(name);
        club.setMedicalLegalBasis("2004. évi I. törvény");
        club.setMedicalNoticeUrl("https://example.org/notice");
        return club;
    }

    static void wire(MedicalRequirementService requirement, MedicalAccessService access, UUID profileId, Club club) {
        when(requirement.readyRequiringClubs(profileId)).thenReturn(List.of(club));
        when(requirement.readyRequiringClubIds(profileId)).thenReturn(Set.of(club.getId()));
        when(requirement.fileRequiredFor(eq(profileId), any())).thenAnswer(call -> {
            java.util.Collection<?> clubs = call.getArgument(1);
            return clubs != null && !clubs.isEmpty() && requirement.fileRequired(profileId);
        });
        when(requirement.asksForFile(profileId, club.getId())).thenAnswer(call -> requirement.fileRequired(profileId));
        if (access == null) {
            return;
        }
        when(access.actorClubsFor(any(), eq(profileId))).thenReturn(Set.of(club.getId()));
        when(access.clubAdminClubsFor(any(), eq(profileId))).thenAnswer(call ->
                access.isClubAdminRequiringProfile(call.<User>getArgument(0), profileId) ? Set.of(club.getId()) : Set.of());
        when(access.isClubAdminSeeing(any(), eq(profileId), any())).thenAnswer(call ->
                sees(call.getArgument(2), club) && access.isClubAdminRequiringProfile(call.<User>getArgument(0), profileId));
        when(access.isClubAdminSeeingFile(any(), eq(profileId), any())).thenAnswer(call ->
                sees(call.getArgument(2), club) && access.isClubAdminRequiringFile(call.<User>getArgument(0), profileId));
        when(access.isCoachSeeing(any(), eq(profileId), any())).thenAnswer(call ->
                sees(call.getArgument(2), club) && access.isCoachRequiringProfile(call.<User>getArgument(0), profileId));
        when(access.isCoachWithFileAccessSeeing(any(), eq(profileId), any())).thenAnswer(call ->
                sees(call.getArgument(2), club) && access.isCoachWithFileAccess(call.<User>getArgument(0), profileId));
    }

    /**
     * A row the tests build without recipients counts as meant for the club, as a row from before the binding does
     * after V23; a row with recipients is seen only when the club is one of them.
     */
    private static boolean sees(MedicalCertificate row, Club club) {
        return row != null && (row.getClubIds().isEmpty() || row.getClubIds().contains(club.getId()));
    }
}
