package com.example.footballanalysis.service;

import ch.qos.logback.classic.Level;
import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.Corner;
import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.requests.UpdateMatchRequest;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import com.example.footballanalysis.model.db.user.User;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.responses.MatchResponse;
import com.example.footballanalysis.model.responses.PageResponse;

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

        PageResponse<MatchResponse> page = matchService.getAllMatches(null, null, null, null, null);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    void getAllMatches_filtersToTheRequestedView() {
        UUID coachTeamId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        User actor = org.mockito.Mockito.mock(User.class);
        when(actor.getRole()).thenReturn(UserRole.COACH);
        when(actor.getId()).thenReturn(actorId);
        when(userAccessService.getAccessibleTeamIds(actor, UserRole.COACH)).thenReturn(Set.of(coachTeamId));
        Match coachMatch = match(UUID.randomUUID());
        Team home = new Team();
        home.setId(coachTeamId);
        home.setName("Coach FC");
        coachMatch.setHomeTeam(home);
        when(matchRepository.findPageByTeamIdsOrOwnUpload(eq(Set.of(coachTeamId)), eq(actorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(coachMatch)));

        PageResponse<MatchResponse> matches = createService().getAllMatches(actor, UserRole.COACH, null, null, null);

        assertThat(matches.items()).extracting(MatchResponse::id).containsExactly(coachMatch.getId());
        verify(userAccessService, never()).getAccessibleTeamIds(actor);
        verify(matchRepository, never()).findAll();
    }

    @Test
    void getAllMatches_parentViewUsesTheRequestedChildTeams() {
        UUID childTeamId = UUID.randomUUID();
        UUID otherTeamId = UUID.randomUUID();
        User actor = org.mockito.Mockito.mock(User.class);
        when(actor.getRole()).thenReturn(UserRole.PARENT);
        when(userAccessService.getAccessibleTeamIds(actor, UserRole.PARENT)).thenReturn(Set.of(childTeamId));
        when(matchRepository.findPageByTeamIds(eq(Set.of(childTeamId)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(createService().getAllMatches(actor, UserRole.PARENT, null, null, null).items()).isEmpty();
        assertThat(createService().getAllMatches(actor, UserRole.PARENT, List.of(childTeamId, otherTeamId), 1, 20).items()).isEmpty();
        verify(matchRepository).findPageByTeamIds(eq(Set.of(childTeamId)), any(Pageable.class));
        verify(matchRepository, never()).findAll();
        verify(matchRepository, never()).findPageOfOwnTeamlessUploads(any(), any());
    }

    @Test
    void getAllMatches_includesActorUploadsWhenViewIsSet() {
        UUID teamId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        User actor = org.mockito.Mockito.mock(User.class);
        when(actor.getRole()).thenReturn(UserRole.COACH);
        when(actor.getId()).thenReturn(actorId);
        when(userAccessService.getAccessibleTeamIds(actor, UserRole.COACH)).thenReturn(Set.of(teamId));
        Match uploaded = match(UUID.randomUUID());
        when(matchRepository.findPageByTeamIdsOrOwnUpload(eq(Set.of(teamId)), eq(actorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(uploaded)));

        PageResponse<MatchResponse> matches = createService().getAllMatches(actor, UserRole.COACH, null, null, null);

        assertThat(matches.items()).extracting(MatchResponse::id).containsExactly(uploaded.getId());
        verify(matchRepository).findPageByTeamIdsOrOwnUpload(eq(Set.of(teamId)), eq(actorId), any(Pageable.class));
        verify(matchRepository, never()).findAll();
    }

    @Test
    void getAllMatches_secondPageDoesNotRepeatTheFirst() {
        UUID teamId = UUID.randomUUID();
        User actor = org.mockito.Mockito.mock(User.class);
        when(actor.getRole()).thenReturn(UserRole.COACH);
        when(actor.getId()).thenReturn(UUID.randomUUID());
        when(userAccessService.getAccessibleTeamIds(actor, UserRole.COACH)).thenReturn(Set.of(teamId));
        List<Match> all = List.of(match(UUID.randomUUID()), match(UUID.randomUUID()), match(UUID.randomUUID()));
        when(matchRepository.findPageByTeamIdsOrOwnUpload(eq(Set.of(teamId)), any(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(2);
                    int start = (int) pageable.getOffset();
                    int end = Math.min(start + pageable.getPageSize(), all.size());
                    List<Match> slice = start >= all.size() ? List.of() : all.subList(start, end);
                    return new PageImpl<>(slice, pageable, all.size());
                });

        MatchService service = createService();
        PageResponse<MatchResponse> first = service.getAllMatches(actor, UserRole.COACH, null, 1, 2);
        PageResponse<MatchResponse> second = service.getAllMatches(actor, UserRole.COACH, null, 2, 2);

        assertThat(first.total()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.items()).hasSize(2);
        assertThat(second.items()).hasSize(1);
        assertThat(second.items().getFirst().id()).isNotIn(first.items().stream().map(MatchResponse::id).toList());
    }

    @Test
    void getAllMatches_rejectsASizeAboveTheMaximum() {
        assertThatThrownBy(() -> createService().getAllMatches(null, null, null, 1, 51))
                .isInstanceOf(BadRequestException.class);
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
    void initiateMatchUpload_rejectsTheSameTeamOnBothSides() {
        UUID teamId = UUID.randomUUID();
        Team team = team(teamId, "Home FC");
        UploadMatchRequest request = uploadRequest(
                "match.mp4",
                teamId,
                teamId,
                LocalDateTime.of(2026, 3, 23, 12, 0));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> createService().initiateMatchUpload(request, null))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getMessageCode())
                .isEqualTo("error.validation.invalid_request");
        verify(matchRepository, never()).save(any());
    }

    @Test
    void updateMatch_rejectsSettingHomeToTheCurrentAwayTeam() {
        UUID matchId = UUID.randomUUID();
        UUID homeId = UUID.randomUUID();
        UUID awayId = UUID.randomUUID();
        Match match = match(matchId);
        match.setHomeTeam(team(homeId, "Home FC"));
        match.setAwayTeam(team(awayId, "Away FC"));
        User actor = org.mockito.Mockito.mock(User.class);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        UpdateMatchRequest request = new UpdateMatchRequest(awayId, null, null, null, null);

        assertThatThrownBy(() -> createService().updateMatch(matchId, request, actor))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getMessageCode())
                .isEqualTo("error.validation.invalid_request");
        verify(matchRepository, never()).save(any());
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
        when(matchRepository.findByIdForUpdate(matchId)).thenReturn(Optional.of(match));

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
        when(matchRepository.findByIdForUpdate(matchId)).thenReturn(Optional.of(match));

        MatchService.PreprocessResult result = createService().markMatchAsPreprocessing("match.mp4");

        assertThat(result.dispatchFieldDetection()).isFalse();
        verify(matchRepository, never()).save(match);
    }

    @Test
    void markMatchAsPreprocessing_skipsSecondWebhookWhilePreprocessing() {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId);
        match.setOverallStatus("PREPROCESSING");

        when(matchRepository.findBySavedMinioFileName("match.mp4")).thenReturn(Optional.of(match));
        when(matchRepository.findByIdForUpdate(matchId)).thenReturn(Optional.of(match));

        MatchService.PreprocessResult result = createService().markMatchAsPreprocessing("match.mp4");

        assertThat(result.dispatchFieldDetection()).isFalse();
        verify(matchRepository, never()).save(match);
    }

    @Test
    void applyFieldDetectionResult_ignoresLateResultAfterCorners() {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId);
        match.setOverallStatus("AWAITING_CORNERS");
        when(matchRepository.findByIdForUpdate(matchId)).thenReturn(Optional.of(match));

        boolean applied = createService().applyFieldDetectionResult(matchId, "http://img", "[]");

        assertThat(applied).isFalse();
        assertThat(match.getOverallStatus()).isEqualTo("AWAITING_CORNERS");
        verify(matchRepository, never()).save(match);
    }

    @Test
    void markFieldDetectionFailed_ignoresLateFailureAfterCompleted() {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId);
        match.setOverallStatus("COMPLETED");
        when(matchRepository.findByIdForUpdate(matchId)).thenReturn(Optional.of(match));

        boolean applied = createService().markFieldDetectionFailed(matchId, "late");

        assertThat(applied).isFalse();
        assertThat(match.getOverallStatus()).isEqualTo("COMPLETED");
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

        when(matchRepository.findByIdForUpdate(matchId)).thenReturn(Optional.of(match));

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

        when(matchRepository.findByIdForUpdate(matchId)).thenReturn(Optional.of(match));

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

        when(matchRepository.findByIdForUpdate(matchId)).thenReturn(Optional.of(match));

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