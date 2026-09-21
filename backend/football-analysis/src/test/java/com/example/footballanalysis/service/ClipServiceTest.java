package com.example.footballanalysis.service;

import com.example.footballanalysis.config.RabbitMQConfig;
import com.example.footballanalysis.dto.ClipRenderStartMessage;
import com.example.footballanalysis.exception.BadRequestException;
import com.example.footballanalysis.exception.ExternalServiceException;
import com.example.footballanalysis.exception.ForbiddenException;
import com.example.footballanalysis.exception.NotFoundException;
import com.example.footballanalysis.model.clip.ClipSyncEvent;
import com.example.footballanalysis.model.clip.ClipSyncEventType;
import com.example.footballanalysis.model.db.Clip;
import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.user.Coach;
import com.example.footballanalysis.model.db.user.UserRole;
import com.example.footballanalysis.model.requests.ClipCompositionUploadRequest;
import com.example.footballanalysis.model.requests.ClipCreateRequest;
import com.example.footballanalysis.model.requests.ClipCreateWithUploadRequest;
import com.example.footballanalysis.model.requests.ClipUpdateRequest;
import com.example.footballanalysis.model.responses.ClipCompositionUploadResponse;
import com.example.footballanalysis.model.responses.ClipResponse;
import com.example.footballanalysis.repository.ClipRepository;
import com.example.footballanalysis.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClipService ACL")
class ClipServiceTest {

    @Mock private ClipRepository clipRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private S3PresignerService videoStorageService;
    @Mock private MinioObjectCleanupService minioObjectCleanupService;
    @Mock private AuditEventService auditEventService;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private UserAccessService userAccessService;

    @InjectMocks
    private ClipService clipService;

    @BeforeEach
    void buckets() {
        ReflectionTestUtils.setField(clipService, "clipsBucket", "clips");
        ReflectionTestUtils.setField(clipService, "rawVideosBucket", "raw-videos");
    }

    @Test
    void getClipsForMatch_requiresMatchAccessThenReturnsClips() {
        UUID matchId = UUID.randomUUID();
        Coach coach = coach();
        Match match = match(matchId);
        Clip clip = clip(UUID.randomUUID(), match, coach);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(clipRepository.findAllByMatch_IdOrderByCreatedAtDesc(matchId)).thenReturn(List.of(clip));

        List<ClipResponse> clips = clipService.getClipsForMatch(matchId, coach);

        verify(userAccessService).requireMatchAccess(coach, matchId);
        assertThat(clips).hasSize(1);
        assertThat(clips.getFirst().matchId()).isEqualTo(matchId);
        assertThat(clips.getFirst().renderedStoragePath()).isEqualTo("clips/" + matchId + "/" + clip.getId() + "/rendered.mp4");
    }

    @Test
    void getClipsForMatch_doesNotListWhenAccessDenied() {
        UUID matchId = UUID.randomUUID();
        Coach stranger = coach();
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match(matchId)));
        doThrow(new ForbiddenException("error.auth.forbidden", new Object[0], "denied"))
                .when(userAccessService).requireMatchAccess(stranger, matchId);

        assertThatThrownBy(() -> clipService.getClipsForMatch(matchId, stranger))
                .isInstanceOf(ForbiddenException.class);

        verify(clipRepository, never()).findAllByMatch_IdOrderByCreatedAtDesc(any());
    }

    @Test
    void generateRenderedClipDownloadUrl_requiresClipAccessAndPresignsPrivateBucket() {
        UUID clipId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        Coach viewer = coach();
        Clip clip = clip(clipId, match(matchId), viewer);
        when(clipRepository.findById(clipId)).thenReturn(Optional.of(clip));
        when(videoStorageService.generateDownloadUrl(eq("clips"), eq(matchId + "/" + clipId + "/rendered.mp4"), eq(Duration.ofMinutes(10))))
                .thenReturn("https://minio/presigned");

        String url = clipService.generateRenderedClipDownloadUrl(clipId, viewer);

        verify(userAccessService).requireClipAccess(viewer, clipId);
        assertThat(url).isEqualTo("https://minio/presigned");
    }

    @Test
    void generateRenderedClipDownloadUrl_doesNotPresignWhenAccessDenied() {
        UUID clipId = UUID.randomUUID();
        Coach stranger = coach();
        when(clipRepository.findById(clipId)).thenReturn(Optional.of(clip(clipId, match(UUID.randomUUID()), coach())));
        doThrow(new ForbiddenException("error.auth.forbidden", new Object[0], "denied"))
                .when(userAccessService).requireClipAccess(stranger, clipId);

        assertThatThrownBy(() -> clipService.generateRenderedClipDownloadUrl(clipId, stranger))
                .isInstanceOf(ForbiddenException.class);

        verify(videoStorageService, never()).generateDownloadUrl(any(), any(), any());
    }

    @Test
    void createClip_requiresManageAccess() {
        UUID matchId = UUID.randomUUID();
        Coach coach = coach();
        Match match = match(matchId);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(clipRepository.save(any(Clip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClipResponse response = clipService.createClip(matchId, new ClipCreateRequest(matchId, "Cut 1", List.of()), coach);

        verify(userAccessService).requireMatchManageAccess(coach, matchId);
        ArgumentCaptor<Clip> captor = ArgumentCaptor.forClass(Clip.class);
        verify(clipRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(coach);
        assertThat(captor.getValue().getRenderStatus()).isEqualTo("PENDING_UPLOAD");
        assertThat(response.name()).contains("Cut 1");
    }

    @Test
    void deleteClip_requiresManageAccessAndCleansMinio() {
        UUID clipId = UUID.randomUUID();
        Coach coach = coach();
        Clip clip = clip(clipId, match(UUID.randomUUID()), coach);
        when(clipRepository.findById(clipId)).thenReturn(Optional.of(clip));

        clipService.deleteClip(clipId, coach);

        verify(userAccessService).requireClipManageAccess(coach, clipId);
        verify(minioObjectCleanupService).deleteClipArtifact(clip);
        verify(clipRepository).delete(clip);
    }

    @Test
    void getClipById_missingClip_throwsNotFound() {
        UUID clipId = UUID.randomUUID();
        when(clipRepository.findById(clipId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clipService.getClipById(clipId, coach()))
                .isInstanceOf(NotFoundException.class);

        verify(userAccessService, never()).requireClipAccess(any(), any());
    }

    @Test
    void initiateCompositionUpload_returnsPresignedAssetUrls() {
        UUID matchId = UUID.randomUUID();
        UUID clipId = UUID.randomUUID();
        Coach coach = coach();
        Clip clip = clip(clipId, match(matchId), coach);
        when(clipRepository.findById(clipId)).thenReturn(Optional.of(clip));
        when(videoStorageService.generateClipUploadUrl(matchId + "/" + clipId + "/overlay.webm", "video/webm"))
                .thenReturn("https://minio/overlay");
        when(videoStorageService.generateClipUploadUrl(matchId + "/" + clipId + "/audio.webm", "audio/webm"))
                .thenReturn("https://minio/audio");
        when(videoStorageService.generateClipUploadUrl(matchId + "/" + clipId + "/timeline.json", "application/json"))
                .thenReturn("https://minio/timeline");

        ClipCompositionUploadResponse response = clipService.initiateCompositionUpload(
                clipId, new ClipCompositionUploadRequest("o.webm", "a.webm", "t.json"), coach);

        verify(userAccessService).requireClipManageAccess(coach, clipId);
        assertThat(clip.getRenderStatus()).isEqualTo("PENDING_UPLOAD");
        assertThat(response.clipId()).isEqualTo(clipId);
        assertThat(response.overlayUploadUrl()).isEqualTo("https://minio/overlay");
        assertThat(response.audioUploadUrl()).isEqualTo("https://minio/audio");
        assertThat(response.timelineUploadUrl()).isEqualTo("https://minio/timeline");
    }

    @Test
    void createClipWithUploadLinks_createsClipThenPresigns() {
        UUID matchId = UUID.randomUUID();
        Coach coach = coach();
        Match match = match(matchId);
        AtomicReference<Clip> saved = new AtomicReference<>();
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(clipRepository.save(any(Clip.class))).thenAnswer(invocation -> {
            Clip c = invocation.getArgument(0);
            saved.set(c);
            return c;
        });
        when(clipRepository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(videoStorageService.generateClipUploadUrl(any(), eq("video/webm"))).thenReturn("overlay");
        when(videoStorageService.generateClipUploadUrl(any(), eq("audio/webm"))).thenReturn("audio");
        when(videoStorageService.generateClipUploadUrl(any(), eq("application/json"))).thenReturn("timeline");

        ClipCompositionUploadResponse response = clipService.createClipWithUploadLinks(
                matchId,
                new ClipCreateWithUploadRequest(matchId, "Cut", List.of(), "o.webm", "a.webm", "t.json"),
                coach);

        verify(userAccessService).requireMatchManageAccess(coach, matchId);
        verify(userAccessService).requireClipManageAccess(eq(coach), any());
        assertThat(response.overlayUploadUrl()).isEqualTo("overlay");
        assertThat(saved.get().getName()).isEqualTo("Cut");
    }

    @Test
    void completeCompositionUpload_queuesRenderJob() {
        UUID matchId = UUID.randomUUID();
        UUID clipId = UUID.randomUUID();
        Coach coach = coach();
        Match match = match(matchId);
        match.setSavedMinioFileName("game.mp4");
        Clip clip = clip(clipId, match, coach);
        when(clipRepository.findById(clipId)).thenReturn(Optional.of(clip));

        ClipResponse response = clipService.completeCompositionUpload(clipId, coach);

        verify(userAccessService).requireClipManageAccess(coach, clipId);
        assertThat(clip.getRenderStatus()).isEqualTo("QUEUED");
        assertThat(response.renderStatus()).isEqualTo("QUEUED");
        ArgumentCaptor<ClipRenderStartMessage> captor = ArgumentCaptor.forClass(ClipRenderStartMessage.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.CLIP_RENDER_ROUTING_KEY),
                captor.capture());
        assertThat(captor.getValue().matchId()).isEqualTo(matchId.toString());
        assertThat(captor.getValue().clipId()).isEqualTo(clipId.toString());
        assertThat(captor.getValue().baseBucket()).isEqualTo("raw-videos");
        assertThat(captor.getValue().baseObjectKey()).isEqualTo("game.mp4");
        assertThat(captor.getValue().outputBucket()).isEqualTo("clips");
        assertThat(captor.getValue().action()).isEqualTo("START-CLIP-RENDER");
    }

    @Test
    void completeCompositionUpload_requiresBaseMatchVideo() {
        UUID clipId = UUID.randomUUID();
        Coach coach = coach();
        Clip clip = clip(clipId, match(UUID.randomUUID()), coach);
        when(clipRepository.findById(clipId)).thenReturn(Optional.of(clip));

        assertThatThrownBy(() -> clipService.completeCompositionUpload(clipId, coach))
                .isInstanceOf(BadRequestException.class);

        verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(Object.class));
    }

    @Test
    void completeCompositionUpload_wrapsBrokerFailure() {
        UUID clipId = UUID.randomUUID();
        Coach coach = coach();
        Match match = match(UUID.randomUUID());
        match.setSavedMinioFileName("game.mp4");
        when(clipRepository.findById(clipId)).thenReturn(Optional.of(clip(clipId, match, coach)));
        doThrow(new AmqpException("broker down")).when(rabbitTemplate)
                .convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME), eq(RabbitMQConfig.CLIP_RENDER_ROUTING_KEY), any(ClipRenderStartMessage.class));

        assertThatThrownBy(() -> clipService.completeCompositionUpload(clipId, coach))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void markRenderLifecycle_updatesStatusAndAuditsCompletion() {
        UUID clipId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        Coach coach = coach();
        Clip clip = clip(clipId, match(matchId), coach);
        when(clipRepository.findById(clipId)).thenReturn(Optional.of(clip));

        clipService.markRenderProcessing(clipId);
        assertThat(clip.getRenderStatus()).isEqualTo("PROCESSING");

        clipService.markRenderFailed(clipId, "ffmpeg died");
        assertThat(clip.getRenderStatus()).isEqualTo("FAILED");
        assertThat(clip.getRenderError()).isEqualTo("ffmpeg died");

        clipService.markRenderCompleted(clipId);
        assertThat(clip.getRenderStatus()).isEqualTo("COMPLETED");
        assertThat(clip.getRenderedAt()).isNotNull();
        verify(auditEventService).record(
                eq("CLIP_CREATED"),
                eq(coach.getId()),
                eq("COACH"),
                eq("CLIP"),
                eq(clipId.toString()),
                eq("matchId=" + matchId + ", name=clip"));
    }

    @Test
    void updateClip_renamesAndRejectsInvalidSyncOrder() {
        UUID clipId = UUID.randomUUID();
        Coach coach = coach();
        Clip clip = clip(clipId, match(UUID.randomUUID()), coach);
        when(clipRepository.findById(clipId)).thenReturn(Optional.of(clip));

        ClipResponse renamed = clipService.updateClip(clipId, new ClipUpdateRequest("New name", null), coach);
        assertThat(renamed.name()).isEqualTo("New name");

        assertThatThrownBy(() -> clipService.updateClip(clipId, new ClipUpdateRequest("  ", null), coach))
                .isInstanceOf(BadRequestException.class);

        List<ClipSyncEvent> backwards = List.of(
                new ClipSyncEvent(2.0, ClipSyncEventType.PLAY, 2.0),
                new ClipSyncEvent(1.0, ClipSyncEventType.PAUSE, 1.0));
        assertThatThrownBy(() -> clipService.updateClip(clipId, new ClipUpdateRequest(null, backwards), coach))
                .isInstanceOf(BadRequestException.class);
    }

    private static Coach coach() {
        Coach coach = new Coach();
        coach.setId(UUID.randomUUID());
        coach.setRole(UserRole.COACH);
        return coach;
    }

    private static Match match(UUID id) {
        Match match = new Match();
        match.setId(id);
        return match;
    }

    private static Clip clip(UUID id, Match match, Coach createdBy) {
        Clip clip = new Clip();
        clip.setId(id);
        clip.setMatch(match);
        clip.setCreatedBy(createdBy);
        clip.setName("clip");
        return clip;
    }
}
