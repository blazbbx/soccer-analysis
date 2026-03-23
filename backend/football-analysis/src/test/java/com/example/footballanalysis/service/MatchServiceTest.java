package com.example.footballanalysis.service;

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

    private MatchService createService() {
        return new MatchService(
                matchRepository,
                teamRepository,
                clipRepository,
                matchSquadMemberRepository,
                videoStorageService,
                minioObjectCleanupService);
    }

    @Test
    void deleteMatch_removesRelatedRowsAndArtifacts() {
        UUID matchId = UUID.randomUUID();
        Match match = match(matchId);
        Clip clip = clip(UUID.randomUUID(), match, "http://localhost:9000/clips/" + matchId + "/clip.mp4");

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(clipRepository.findAllByMatch_IdIn(List.of(matchId))).thenReturn(List.of(clip));

        MatchService matchService = createService();
        matchService.deleteMatch(matchId);

        verify(clipRepository).deleteAllByMatch_IdIn(List.of(matchId));
        verify(matchSquadMemberRepository).deleteAllByMatch_Id(matchId);
        verify(matchRepository).delete(match);
        verify(matchRepository).flush();
        verify(minioObjectCleanupService).deleteMatchArtifacts(match, List.of(clip));
    }

    @Test
    void deleteMatch_throwsWhenMissing() {
        UUID matchId = UUID.randomUUID();
        when(matchRepository.findById(matchId)).thenReturn(Optional.empty());

        MatchService matchService = createService();

        assertThatThrownBy(() -> matchService.deleteMatch(matchId))
                .isInstanceOf(NotFoundException.class);

        verify(clipRepository, never()).findAllByMatch_IdIn(any());
    }

    @Test
    void initiateMatchUpload_allowsMissingTeamIds() {
        UUID matchId = UUID.randomUUID();
        UploadMatchRequest request = new UploadMatchRequest(
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
        assertThat(matchCaptor.getValue().getSavedMinioFileName()).endsWith(".mp4");
        verify(videoStorageService).generateUploadUrl(matchCaptor.getValue().getSavedMinioFileName());
        verify(teamRepository, never()).findById(any());
    }

    @Test
    void initiateMatchUpload_usesProvidedTeamsWhenPresent() {
        UUID matchId = UUID.randomUUID();
        UUID homeTeamId = UUID.randomUUID();
        UUID awayTeamId = UUID.randomUUID();
        Team homeTeam = team(homeTeamId, "Home FC");
        Team awayTeam = team(awayTeamId, "Away FC");
        UploadMatchRequest request = new UploadMatchRequest(
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
    }

    @Test
    void initiateMatchUpload_rejectsBlankOriginalFilename() {
        UploadMatchRequest request = new UploadMatchRequest(
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
        UploadMatchRequest request = new UploadMatchRequest(
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
        UploadMatchRequest request = new UploadMatchRequest(
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

    private Clip clip(UUID id, Match match, String minioUrl) {
        Clip clip = new Clip();
        clip.setId(id);
        clip.setMatch(match);
        clip.setMinioUrl(minioUrl);
        return clip;
    }
}