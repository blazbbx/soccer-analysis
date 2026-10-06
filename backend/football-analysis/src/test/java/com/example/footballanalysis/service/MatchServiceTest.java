package com.example.footballanalysis.service;

import ch.qos.logback.classic.Level;
import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.Corner;
import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.requests.UploadMatchRequest;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.MatchSquadMemberRepository;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.testsupport.LogCaptureSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.MatchResponse;

import com.example.footballanalysis.repository.PlayerParentRepository;
import com.example.footballanalysis.repository.PlayerProfileRepository;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchService tesztek")
class MatchServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private ClipRepository clipRepository;

    @Mock
    private MatchSquadMemberRepository matchSquadMemberRepository;

    @Mock
    private S3PresignerService videoStorageService;

    @Mock
    private MinioObjectCleanupService minioObjectCleanupService;

    @Mock
    private AuditEventService auditEventService;

    @Mock
    private UserAccessService userAccessService;

    @Mock
    private S3Client s3Client;

    @Mock
    private PlayerProfileRepository playerProfileRepository;

    @Mock
    private PlayerParentRepository playerParentRepository;

    private MatchService createService() {
        return new MatchService(
                matchRepository,
                teamRepository,
                clipRepository,
                matchSquadMemberRepository,
                videoStorageService,
                minioObjectCleanupService,
                auditEventService,
                userAccessService,
                new ObjectMapper(),
                s3Client,
                playerProfileRepository,
                playerParentRepository);
    }

    @Test
    void getAllMatches_returnsEmptyListWhenRepositoryReturnsNull() {
        MatchService matchService = createService();

        assertThat(matchService.getAllMatches(null)).isEmpty();
    }

    @Test
    void getAllMatches_filtersToTheRequestedView() {
        UUID coachTeamId = UUID.randomUUID();
        User actor = org.mockito.Mockito.mock(User.class);
        when(actor.getRole()).thenReturn(UserRole.COACH);
        when(userAccessService.getAccessibleTeamIds(actor, UserRole.COACH)).thenReturn(Set.of(coachTeamId));
        Match coachMatch = match(UUID.randomUUID());
        Team home = new Team();
        home.setId(coachTeamId);
        home.setName("Coach FC");
        coachMatch.setHomeTeam(home);
        when(matchRepository.findAllByHomeTeam_IdOrAwayTeam_Id(coachTeamId, coachTeamId)).thenReturn(List.of(coachMatch));

        List<MatchResponse> matches = createService().getAllMatches(actor, UserRole.COACH);

        assertThat(matches).extracting(MatchResponse::id).containsExactly(coachMatch.getId());
        verify(userAccessService, never()).getAccessibleTeamIds(actor);
        verify(matchRepository, never()).findAllByUploadedBy_Id(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getAllMatches_parentViewUsesChildTeamScope() {
        UUID childTeamId = UUID.randomUUID();
        User actor = org.mockito.Mockito.mock(User.class);
        when(actor.getRole()).thenReturn(UserRole.PARENT);
        when(userAccessService.getAccessibleTeamIds(actor, UserRole.PARENT)).thenReturn(Set.of(childTeamId));
        when(matchRepository.findAllByHomeTeam_IdOrAwayTeam_Id(childTeamId, childTeamId)).thenReturn(List.of());

        assertThat(createService().getAllMatches(actor, UserRole.PARENT)).isEmpty();
        verify(userAccessService).getAccessibleTeamIds(actor, UserRole.PARENT);
        verify(userAccessService, never()).getAccessibleTeamIds(actor);
    }

    @Test
    void getAllMatches_includesActorUploadsWhenViewIsSet() {
        UUID teamId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        User actor = org.mockito.Mockito.mock(User.class);
        when(actor.getRole()).thenReturn(UserRole.COACH);
        when(actor.getId()).thenReturn(actorId);
        when(userAccessService.getAccessibleTeamIds(actor, UserRole.COACH)).thenReturn(Set.of(teamId));
        when(matchRepository.findAllByHomeTeam_IdOrAwayTeam_Id(teamId, teamId)).thenReturn(List.of());
        Match uploaded = match(UUID.randomUUID());
        Match tiedToOtherTeam = match(UUID.randomUUID());
        tiedToOtherTeam.setHomeTeam(team(UUID.randomUUID(), "Other FC"));
        when(matchRepository.findAllByUploadedBy_Id(actorId)).thenReturn(List.of(uploaded, tiedToOtherTeam));

        List<MatchResponse> matches = createService().getAllMatches(actor, UserRole.COACH);

        assertThat(matches).extracting(MatchResponse::id).containsExactly(uploaded.getId());
        verify(matchRepository).findAllByUploadedBy_Id(actorId);
        verify(matchRepository, never()).findAll();
    }

    @Test
    void deleteMatch_removesRelatedRowsAndArtifacts() {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId);
        Clip clip = clip(UUID.randomUUID(), match);

        User actor = org.mockito.Mockito.mock(User.class);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(clipRepository.findAllByMatch_IdIn(List.of(matchId))).thenReturn(List.of(clip));

        MatchService matchService = createService();
        matchService.deleteMatch(matchId, actor);

        verify(clipRepository).deleteAllByMatch_IdIn(List.of(matchId));
        verify(matchSquadMemberRepository).deleteAllByMatch_Id(matchId);
        verify(matchRepository).delete(match);
        verify(matchRepository).flush();
        verify(minioObjectCleanupService).deleteMatchArtifacts(match, List.of(clip));
        verify(auditEventService).record(
            "MATCH_DELETED",
            null,
            null,
            "MATCH",
            matchId.toString(),
            "clipIds=[" + clip.getId() + "]"
        );
    }

    @Test
    void deleteMatch_throwsWhenMissing() {
        UUID matchId = UUID.randomUUID();
        when(matchRepository.findById(matchId)).thenReturn(Optional.empty());
        User actor = org.mockito.Mockito.mock(User.class);

        MatchService matchService = createService();

        assertThatThrownBy(() -> matchService.deleteMatch(matchId, actor))
                .isInstanceOf(NotFoundException.class);

        verify(clipRepository, never()).findAllByMatch_IdIn(any());
    }

    @Test
    void deleteMatch_ShouldDeleteMatch_WhenMatchExists() {
        UUID matchId = UUID.randomUUID();
        Match match = new Match();
        match.setId(matchId);

        User actor = org.mockito.Mockito.mock(User.class);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        MatchService matchService = createService();
        matchService.deleteMatch(matchId, actor);

        verify(matchRepository).delete(match);
        verify(minioObjectCleanupService).deleteMatchArtifacts(match, List.of());
    }

    @Test
    void deleteMatch_ShouldThrowNotFound_WhenMatchDoesNotExist() {
        UUID matchId = UUID.randomUUID();
        User actor = org.mockito.Mockito.mock(User.class);
        when(matchRepository.findById(matchId)).thenReturn(Optional.empty());

        MatchService matchService = createService();

        assertThrows(NotFoundException.class, () -> matchService.deleteMatch(matchId, actor));
    }

    @Test
    void initiateMatchUpload_allowsMissingTeamIds() {
        UUID matchId = UUID.randomUUID();
        UploadMatchRequest request = uploadRequest(
            "match.mp4",
            null,
            null,
            LocalDateTime.of(2026, 3, 23, 12, 0));

        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> {
            Match match = invocation.getArgument(0);
            match.setId(matchId);
            return match;
        });
        when(videoStorageService.generateUploadUrl(any())).thenReturn("http://localhost:9000/raw-videos/upload-url");

        MatchService matchService = createService();
        Map<String, String> result = matchService.initiateMatchUpload(request, null);

        assertThat(result).containsKey("uploadUrl");
        assertThat(result.get("matchId")).isEqualTo(matchId.toString());
        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        assertThat(matchCaptor.getValue().getHomeTeam()).isNull();
        assertThat(matchCaptor.getValue().getAwayTeam()).isNull();
        assertThat(matchCaptor.getValue().getHomeTeamColor()).isEqualTo("Premier");
        assertThat(matchCaptor.getValue().getAwayTeamColor()).isEqualTo("Premier");
        assertThat(matchCaptor.getValue().getRefereeColor()).isEqualTo("Elite");
        assertThat(matchCaptor.getValue().getSavedMinioFileName()).endsWith(".mp4");
        verify(videoStorageService).generateUploadUrl(matchCaptor.getValue().getSavedMinioFileName());
        verify(auditEventService).record(
            "MATCH_CREATED",
            null,
            null,
            "MATCH",
            matchId.toString(),
            "homeTeamId=null, awayTeamId=null"
        );
        verify(teamRepository, never()).findById(any());
    }

    @Test
    void initiateMatchUpload_usesProvidedTeamsWhenPresent() {
        UUID matchId = UUID.randomUUID();
        UUID homeTeamId = UUID.randomUUID();
        UUID awayTeamId = UUID.randomUUID();
        Team homeTeam = team(homeTeamId, "Home FC");
        Team awayTeam = team(awayTeamId, "Away FC");
        UploadMatchRequest request = uploadRequest(
            "match.mp4",
            homeTeamId,
            awayTeamId,
            LocalDateTime.of(2026, 3, 23, 12, 0));

        when(teamRepository.findById(homeTeamId)).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(awayTeamId)).thenReturn(Optional.of(awayTeam));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> {
            Match match = invocation.getArgument(0);
            match.setId(matchId);
            return match;
        });
        when(videoStorageService.generateUploadUrl(any())).thenReturn("http://localhost:9000/raw-videos/upload-url");

        MatchService matchService = createService();
        Map<String, String> result = matchService.initiateMatchUpload(request, null);

        assertThat(result.get("matchId")).isEqualTo(matchId.toString());
        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        assertThat(matchCaptor.getValue().getHomeTeam()).isEqualTo(homeTeam);
        assertThat(matchCaptor.getValue().getAwayTeam()).isEqualTo(awayTeam);
        verify(teamRepository).findById(homeTeamId);
        verify(teamRepository).findById(awayTeamId);
        verify(auditEventService).record(
            "MATCH_CREATED",
            null,
            null,
            "MATCH",
            matchId.toString(),
            "homeTeamId=" + homeTeamId + ", awayTeamId=" + awayTeamId
        );
    }

    @Test
    void initiateMatchUpload_resolvesAwayTeamByName_whenIdMissing() {
        UUID matchId = UUID.randomUUID();
        UploadMatchRequest request = uploadRequest(
            "match.mp4",
            null,
            null,
            "Away FC",
            LocalDateTime.of(2026, 3, 23, 12, 0));

        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> {
            Match match = invocation.getArgument(0);
            match.setId(matchId);
            return match;
        });
        when(videoStorageService.generateUploadUrl(any())).thenReturn("http://localhost:9000/raw-videos/upload-url");

        MatchService matchService = createService();
        matchService.initiateMatchUpload(request, null);

        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        assertThat(matchCaptor.getValue().getAwayTeam()).isNull();
        assertThat(matchCaptor.getValue().getAwayTeamName()).isEqualTo("Away FC");
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void initiateMatchUpload_storesAwayTeamNameWithoutCreatingTeam_whenIdMissing() {
        UUID matchId = UUID.randomUUID();
        UploadMatchRequest request = uploadRequest(
            "match.mp4",
            null,
            null,
            "New Away Team",
            LocalDateTime.of(2026, 3, 23, 12, 0));

        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> {
            Match match = invocation.getArgument(0);
            match.setId(matchId);
            return match;
        });
        when(videoStorageService.generateUploadUrl(any())).thenReturn("http://localhost:9000/raw-videos/upload-url");

        MatchService matchService = createService();
        matchService.initiateMatchUpload(request, null);

        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        assertThat(matchCaptor.getValue().getAwayTeam()).isNull();
        assertThat(matchCaptor.getValue().getAwayTeamName()).isEqualTo("New Away Team");
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void initiateMatchUpload_rejectsBlankOriginalFilename() {
        UploadMatchRequest request = uploadRequest(
            "   ",
            null,
            null,
            LocalDateTime.of(2026, 3, 23, 12, 0));

        MatchService matchService = createService();

        assertThatThrownBy(() -> matchService.initiateMatchUpload(request, null))
                .isInstanceOf(BadRequestException.class);

        verify(matchRepository, never()).save(any());
        verify(videoStorageService, never()).generateUploadUrl(any());
    }

    @Test
    void initiateMatchUpload_rejectsMissingHomeTeam() {
        UUID homeTeamId = UUID.randomUUID();
        UploadMatchRequest request = uploadRequest(
            "match.mp4",
            homeTeamId,
            null,
            LocalDateTime.of(2026, 3, 23, 12, 0));

        when(teamRepository.findById(homeTeamId)).thenReturn(Optional.empty());

        MatchService matchService = createService();

        assertThatThrownBy(() -> matchService.initiateMatchUpload(request, null))
                .isInstanceOf(NotFoundException.class);

        verify(matchRepository, never()).save(any());
        verify(videoStorageService, never()).generateUploadUrl(any());
    }

    @Test
    void initiateMatchUpload_rejectsMissingAwayTeam() {
        UUID awayTeamId = UUID.randomUUID();
        UploadMatchRequest request = uploadRequest(
            "match.mp4",
            null,
            awayTeamId,
            LocalDateTime.of(2026, 3, 23, 12, 0));

        when(teamRepository.findById(awayTeamId)).thenReturn(Optional.empty());

        MatchService matchService = createService();

        assertThatThrownBy(() -> matchService.initiateMatchUpload(request, null))
                .isInstanceOf(NotFoundException.class);

        verify(matchRepository, never()).save(any());
        verify(videoStorageService, never()).generateUploadUrl(any());
    }

    @Test
    void markMatchAsPreprocessing_logsStatusUpdate() {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId);

        when(matchRepository.findBySavedMinioFileName("match.mp4")).thenReturn(Optional.of(match));

        MatchService matchService = createService();

        try (LogCaptureSession logs = LogCaptureSession.capture(MatchService.class, Level.INFO)) {
            matchService.markMatchAsPreprocessing("match.mp4");

            assertThat(logs.events())
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.INFO);
                        assertThat(event.getFormattedMessage()).isEqualTo("Match " + matchId + " status updated to PREPROCESSING (awaiting field detection)");
                    });
        }

        verify(matchRepository).save(match);
    }

    @Test
    void markMatchAsPreprocessing_skipsDispatchWhenAlreadyCompleted() {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId);
        match.setOverallStatus("COMPLETED");

        when(matchRepository.findBySavedMinioFileName("match.mp4")).thenReturn(Optional.of(match));

        MatchService.PreprocessResult result = createService().markMatchAsPreprocessing("match.mp4");

        assertThat(result.dispatchFieldDetection()).isFalse();
        verify(matchRepository, never()).save(match);
    }

    @Test
    void initiateMatchUpload_rejectsDisallowedExtension() {
        UploadMatchRequest request = uploadRequest(
                "match.exe",
                null,
                null,
                LocalDateTime.of(2026, 3, 23, 12, 0));

        assertThatThrownBy(() -> createService().initiateMatchUpload(request, null))
                .isInstanceOf(BadRequestException.class);

        verify(matchRepository, never()).save(any());
    }

    @Test
    void confirmCornersAndStartProcessing_transitionsAwaitingCornersToProcessing() {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId);
        match.setOverallStatus("AWAITING_CORNERS");
        User actor = org.mockito.Mockito.mock(User.class);
        List<Corner> corners = fourCorners();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        MatchService.CornerConfirmResult result = createService()
                .confirmCornersAndStartProcessing(matchId, corners, actor);

        assertThat(result.dispatchWorkers()).isTrue();
        assertThat(result.match().getOverallStatus()).isEqualTo("PROCESSING");
        verify(userAccessService).requireMatchManageAccess(actor, matchId);
        verify(matchRepository).save(match);
    }

    @Test
    void confirmCornersAndStartProcessing_isIdempotentWhenAlreadyProcessing() {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId);
        match.setOverallStatus("PROCESSING");
        User actor = org.mockito.Mockito.mock(User.class);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        MatchService.CornerConfirmResult result = createService()
                .confirmCornersAndStartProcessing(matchId, fourCorners(), actor);

        assertThat(result.dispatchWorkers()).isFalse();
        assertThat(result.match().getOverallStatus()).isEqualTo("PROCESSING");
        verify(userAccessService).requireMatchManageAccess(actor, matchId);
        verify(matchRepository, never()).save(match);
    }

    @Test
    void confirmCornersAndStartProcessing_rejectsWrongState() {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId);
        match.setOverallStatus("PREPROCESSING");
        User actor = org.mockito.Mockito.mock(User.class);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> createService()
                .confirmCornersAndStartProcessing(matchId, fourCorners(), actor))
                .isInstanceOf(BadRequestException.class);

        verify(matchRepository, never()).save(any());
    }

    private List<Corner> fourCorners() {
        return List.of(
                new Corner(0, 0),
                new Corner(100, 0),
                new Corner(100, 50),
                new Corner(0, 50));
    }

    private Team team(UUID id, String name) {
        Team team = new Team();
        team.setId(id);
        team.setName(name);
        return team;
    }

    private Match match(UUID id) {
        Match match = new Match();
        match.setId(id);
        return match;
    }

    private Clip clip(UUID id, Match match) {
        Clip clip = new Clip();
        clip.setId(id);
        clip.setMatch(match);
        return clip;
    }

    private UploadMatchRequest uploadRequest(String originalFilename, UUID homeTeamId, UUID awayTeamId, LocalDateTime matchDate) {
        return uploadRequest(originalFilename, homeTeamId, awayTeamId, null, matchDate);
    }

    private UploadMatchRequest uploadRequest(String originalFilename, UUID homeTeamId, UUID awayTeamId, String awayTeamName, LocalDateTime matchDate) {
        return new UploadMatchRequest(
                originalFilename,
                homeTeamId,
                awayTeamId,
                awayTeamName,
                matchDate,
                "Premier",
                "Premier",
                "Elite"
        );
    }
}