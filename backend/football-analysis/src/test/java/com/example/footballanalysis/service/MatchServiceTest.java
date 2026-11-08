package com.example.footballanalysis.service;

import ch.qos.logback.classic.Level;
import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.Corner;
import com.example.footballanalysis.model.Goal;
import com.example.footballanalysis.model.GoalCorner;
import com.example.footballanalysis.model.JerseyColors;
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
import com.example.footballanalysis.repository.CoachRepository;
import com.example.footballanalysis.repository.FanRepository;
import com.example.footballanalysis.repository.PlayerRepository;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private S3Client s3Client;

    @Mock
    private FanRepository fanRepository;

    @Mock
    private CoachRepository coachRepository;

    @Mock
    private PlayerRepository playerRepository;

    private MatchService createService() {
        return new MatchService(
                matchRepository,
                teamRepository,
                clipRepository,
                matchSquadMemberRepository,
                videoStorageService,
                minioObjectCleanupService,
                auditEventService,
                new ObjectMapper(),
                s3Client,
                fanRepository,
                coachRepository,
                playerRepository);
    }

    @Test
    void getAllMatches_returnsEmptyListWhenRepositoryReturnsNull() {
        MatchService matchService = createService();

        assertThat(matchService.getAllMatches()).isEmpty();
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
    void initiateMatchUpload_allowsMissingColors_becauseTheyAreSuggestedLater() {
        UploadMatchRequest request = new UploadMatchRequest(
                "match.mp4", null, null, null, LocalDateTime.of(2026, 3, 23, 12, 0), null, "  ", null);

        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(videoStorageService.generateUploadUrl(any())).thenReturn("http://localhost:9000/raw-videos/upload-url");

        createService().initiateMatchUpload(request, null);

        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        assertThat(matchCaptor.getValue().getHomeTeamColor()).isNull();
        assertThat(matchCaptor.getValue().getAwayTeamColor()).isNull();
        assertThat(matchCaptor.getValue().getRefereeColor()).isNull();
    }

    @Test
    void applyFieldDetectionResult_storesSuggestedColorsInBlankColors() {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        Match result = createService().applyFieldDetectionResult(
                matchId, "http://img", "[]", new JerseyColors("#D13331", "#103ECD", "#080d0b"));

        assertThat(result.getHomeTeamColor()).isEqualTo("#d13331");
        assertThat(result.getAwayTeamColor()).isEqualTo("#103ecd");
        assertThat(result.getRefereeColor()).isEqualTo("#080d0b");
        assertThat(result.getOverallStatus()).isEqualTo("AWAITING_CORNERS");
    }

    @Test
    void applyFieldDetectionResult_neverOverwritesExistingOrAcceptsMalformedSuggestions() {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId);
        match.setHomeTeamColor("#112233");
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        Match result = createService().applyFieldDetectionResult(
                matchId, "http://img", "[]", new JerseyColors("#d13331", "red", null));

        assertThat(result.getHomeTeamColor()).isEqualTo("#112233");
        assertThat(result.getAwayTeamColor()).isNull();
        assertThat(result.getRefereeColor()).isNull();
    }

    @Test
    void applyFieldDetectionResult_withoutSuggestion_leavesColorsUntouchedAndStillAwaitsCorners() {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        Match result = createService().applyFieldDetectionResult(matchId, "http://img", "[]", null);

        assertThat(result.getHomeTeamColor()).isNull();
        assertThat(result.getOverallStatus()).isEqualTo("AWAITING_CORNERS");
    }

    @Test
    void confirmCornersAndStartProcessing_storesConfirmedColors() {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId);
        match.setOverallStatus("AWAITING_CORNERS");
        match.setHomeTeamColor("#d13331"); // the suggestion; the confirmation must replace it
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        Match result = createService().confirmCornersAndStartProcessing(
                matchId,
                List.of(new Corner(0, 0), new Corner(10, 0), new Corner(10, 10), new Corner(0, 10)),
                new JerseyColors("#aa0000", "#0000aa", "#111111"),
                List.of(),
                null);

        assertThat(result.getHomeTeamColor()).isEqualTo("#aa0000");
        assertThat(result.getAwayTeamColor()).isEqualTo("#0000aa");
        assertThat(result.getRefereeColor()).isEqualTo("#111111");
        assertThat(result.getOverallStatus()).isEqualTo("PROCESSING");
    }

    @Test
    void confirmCornersAndStartProcessing_rejectsBlankColor() {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId);
        match.setOverallStatus("AWAITING_CORNERS");
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> createService().confirmCornersAndStartProcessing(
                matchId,
                List.of(new Corner(0, 0), new Corner(10, 0), new Corner(10, 10), new Corner(0, 10)),
                new JerseyColors("#aa0000", " ", "#111111"),
                List.of(),
                null))
                .isInstanceOf(BadRequestException.class);

        assertThat(match.getOverallStatus()).isEqualTo("AWAITING_CORNERS");
    }

    // ---------- goal detection ----------

    private static final List<Corner> FIELD_CORNERS =
            List.of(new Corner(0, 0), new Corner(10, 0), new Corner(10, 10), new Corner(0, 10));
    private static final JerseyColors COLORS = new JerseyColors("#aa0000", "#0000aa", "#111111");

    private static Goal completeGoal() {
        return new Goal(List.of(
                GoalCorner.confirmed(100, 50), GoalCorner.confirmed(200, 50),
                GoalCorner.confirmed(100, 150), GoalCorner.confirmed(200, 150)));
    }

    @Test
    void markMatchAsPreprocessing_asksForGoalDetectionAndClearsOldGoals() {
        Match match = match(UUID.randomUUID());
        match.setSavedMinioFileName("v.mp4");
        match.setGoalsJson("[]"); // left over from an earlier attempt
        when(matchRepository.findBySavedMinioFileName("v.mp4")).thenReturn(Optional.of(match));

        Match result = createService().markMatchAsPreprocessing("v.mp4");

        assertThat(result.getGoalDetectionStatus()).isEqualTo("PENDING");
        assertThat(result.getGoalsJson()).isNull();
    }

    @Test
    void applyGoalDetectionResult_storesTheGoalsBeforeConfirmation() throws Exception {
        UUID matchId = UUID.randomUUID();
        when(matchRepository.updateGoalDetectionBeforeConfirmation(eq(matchId), eq("COMPLETED"), any()))
                .thenReturn(1);
        // A corner the model was unsure about keeps its null position and its flag.
        Goal partial = new Goal(List.of(
                new GoalCorner(100, 50, "detected"), new GoalCorner(200, 50, "detected"),
                new GoalCorner(100, 150, "clamped"), new GoalCorner(null, null, "needs_manual_review")));

        boolean stored = createService().applyGoalDetectionResult(matchId, List.of(partial));

        assertThat(stored).isTrue();
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(matchRepository).updateGoalDetectionBeforeConfirmation(eq(matchId), eq("COMPLETED"), json.capture());
        Goal roundTrip = new ObjectMapper().readValue(json.getValue(), Goal[].class)[0];
        assertThat(roundTrip.corners().get(3).x()).isNull();
        assertThat(roundTrip.corners().get(3).source()).isEqualTo("needs_manual_review");
        assertThat(roundTrip.corners().get(2).source()).isEqualTo("clamped");
    }

    @Test
    void applyGoalDetectionResult_isDroppedAfterConfirmation() {
        UUID matchId = UUID.randomUUID();
        when(matchRepository.updateGoalDetectionBeforeConfirmation(eq(matchId), any(), any())).thenReturn(0);

        assertThat(createService().applyGoalDetectionResult(matchId, List.of(completeGoal()))).isFalse();
    }

    @Test
    void markGoalDetectionFailed_storesFailedWithoutGoals() {
        UUID matchId = UUID.randomUUID();
        when(matchRepository.updateGoalDetectionBeforeConfirmation(matchId, "FAILED", null)).thenReturn(1);

        assertThat(createService().markGoalDetectionFailed(matchId, "boom")).isTrue();
    }

    @Test
    void confirmCorners_storesTheUsersGoalsAsConfirmed() throws Exception {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId);
        match.setOverallStatus("AWAITING_CORNERS");
        match.setGoalDetectionStatus("COMPLETED");
        match.setGoalsJson("[{\"detected\":\"by the worker\"}]");
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        Match result = createService().confirmCornersAndStartProcessing(
                matchId, FIELD_CORNERS, COLORS, List.of(completeGoal()), null);

        assertThat(result.getGoalDetectionStatus()).isEqualTo("CONFIRMED");
        Goal[] stored = new ObjectMapper().readValue(result.getGoalsJson(), Goal[].class);
        assertThat(stored).hasSize(1);
        assertThat(stored[0].corners()).extracting(GoalCorner::source).containsOnly("confirmed");
        assertThat(stored[0].corners().get(0).x()).isEqualTo(100);
    }

    @Test
    void confirmCorners_withoutGoals_storesAnEmptyConfirmedList() {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId);
        match.setOverallStatus("AWAITING_CORNERS");
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        Match result = createService().confirmCornersAndStartProcessing(matchId, FIELD_CORNERS, COLORS, null, null);

        assertThat(result.getGoalsJson()).isEqualTo("[]");
        assertThat(result.getGoalDetectionStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void confirmCorners_rejectsMoreThanTwoGoalsAndIncompleteGoalsWithoutChangingTheMatch() {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId);
        match.setOverallStatus("AWAITING_CORNERS");
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        Goal missingCorner = new Goal(List.of(
                GoalCorner.confirmed(1, 1), GoalCorner.confirmed(2, 1), GoalCorner.confirmed(1, 2),
                new GoalCorner(null, null, "needs_manual_review")));

        assertThatThrownBy(() -> createService().confirmCornersAndStartProcessing(
                matchId, FIELD_CORNERS, COLORS, List.of(completeGoal(), completeGoal(), completeGoal()), null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> createService().confirmCornersAndStartProcessing(
                matchId, FIELD_CORNERS, COLORS, List.of(missingCorner), null))
                .isInstanceOf(BadRequestException.class);

        assertThat(match.getOverallStatus()).isEqualTo("AWAITING_CORNERS");
        assertThat(match.getGoalsJson()).isNull();
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