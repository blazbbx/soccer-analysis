package com.example.footballanalysis.service;

import ch.qos.logback.classic.Level;
import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.Team;
import com.example.footballanalysis.model.requests.UploadMatchRequest;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.MatchRepository;
import com.example.footballanalysis.repository.MatchSquadMemberRepository;
import com.example.footballanalysis.repository.TeamRepository;
import com.example.footballanalysis.testsupport.LogCaptureSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.oauth2.jwt.Jwt;
import com.example.footballanalysis.repository.UserRepository;

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
    private UserRepository userRepository;

    @Mock
    private S3PresignerService videoStorageService;

    @Mock
    private MinioObjectCleanupService minioObjectCleanupService;

    @Mock
    private AuditEventService auditEventService;

    private MatchService createService() {
        return new MatchService(
                matchRepository,
                teamRepository,
                clipRepository,
                matchSquadMemberRepository,
                userRepository,
                videoStorageService,
                minioObjectCleanupService,
                auditEventService);
    }

    @Test
    void getAllMatches_returnsEmptyListWhenRepositoryReturnsNull() {
        when(matchRepository.findAll()).thenReturn(null);

        MatchService matchService = createService();

        assertThat(matchService.getAllMatches()).isEmpty();
    }

    @Test
    void deleteMatch_removesRelatedRowsAndArtifacts() {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId);
        Clip clip = clip(UUID.randomUUID(), match);

        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(clipRepository.findAllByMatch_IdIn(List.of(matchId))).thenReturn(List.of(clip));

        MatchService matchService = createService();
        matchService.deleteMatch(matchId, jwt);

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
        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);

        MatchService matchService = createService();

        assertThatThrownBy(() -> matchService.deleteMatch(matchId, jwt))
                .isInstanceOf(NotFoundException.class);

        verify(clipRepository, never()).findAllByMatch_IdIn(any());
    }

    @Test
    void deleteMatch_ShouldDeleteMatch_WhenMatchExists() {
        UUID matchId = UUID.randomUUID();
        Match match = new Match();
        match.setId(matchId);

        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        MatchService matchService = createService();
        matchService.deleteMatch(matchId, jwt);

        verify(matchRepository).delete(match);
        verify(minioObjectCleanupService).deleteMatchArtifacts(match, List.of());
    }

    @Test
    void deleteMatch_ShouldThrowNotFound_WhenMatchDoesNotExist() {
        UUID matchId = UUID.randomUUID();
        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
        when(matchRepository.findById(matchId)).thenReturn(Optional.empty());

        MatchService matchService = createService();

        assertThrows(NotFoundException.class, () -> matchService.deleteMatch(matchId, jwt));
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
        Map<String, String> result = matchService.initiateMatchUpload(request);

        assertThat(result).containsKey("uploadUrl");
        assertThat(result.get("matchId")).isEqualTo(matchId.toString());
        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        assertThat(matchCaptor.getValue().getHomeTeam()).isNull();
        assertThat(matchCaptor.getValue().getAwayTeam()).isNull();
        assertThat(matchCaptor.getValue().getHomeTeamColor()).isEqualTo("Premier");
        assertThat(matchCaptor.getValue().getAwayTeamColor()).isEqualTo("Premier");
        assertThat(matchCaptor.getValue().getRefereeColor()).isEqualTo("Elite");
        assertThat(matchCaptor.getValue().getHomeTeamShortsColor()).isNull();
        assertThat(matchCaptor.getValue().getHomeTeamSocksColor()).isNull();
        assertThat(matchCaptor.getValue().getAwayTeamShortsColor()).isNull();
        assertThat(matchCaptor.getValue().getAwayTeamSocksColor()).isNull();
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
        Map<String, String> result = matchService.initiateMatchUpload(request);

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
        matchService.initiateMatchUpload(request);

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
        matchService.initiateMatchUpload(request);

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

        assertThatThrownBy(() -> matchService.initiateMatchUpload(request))
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

        assertThatThrownBy(() -> matchService.initiateMatchUpload(request))
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

        assertThatThrownBy(() -> matchService.initiateMatchUpload(request))
                .isInstanceOf(NotFoundException.class);

        verify(matchRepository, never()).save(any());
        verify(videoStorageService, never()).generateUploadUrl(any());
    }

    @Test
    void markMatchAsProcessing_logsStatusUpdate() {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId);

        when(matchRepository.findBySavedMinioFileName("match.mp4")).thenReturn(Optional.of(match));

        MatchService matchService = createService();

        try (LogCaptureSession logs = LogCaptureSession.capture(MatchService.class, Level.INFO)) {
            matchService.markMatchAsProcessing("match.mp4");

            assertThat(logs.events())
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.INFO);
                        assertThat(event.getFormattedMessage()).isEqualTo("Match " + matchId + " status updated to PROCESSING");
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
                "Elite",
                null,
                null,
                null,
                null
        );
    }
}