package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.Match;
import com.example.footballanalysis.model.db.StorageDeletion;
import com.example.footballanalysis.repository.StorageDeletionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B15: match files go only after the database delete committed, and a failed delete stays queued. */
@ExtendWith(MockitoExtension.class)
class MinioObjectCleanupQueueTest {

    @Mock private S3Client s3Client;
    @Mock private ListObjectsV2Iterable paginator;
    @Mock private StorageDeletionRepository deletionRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private MinioObjectCleanupService service;
    private final UUID matchId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MinioObjectCleanupService(s3Client, "raw-videos", "tracking-data", "hls-streams", "clips",
                "field-detection", deletionRepository, transactionManager, Clock.systemUTC());
        lenient().when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenReturn(paginator);
        lenient().when(paginator.iterator()).thenReturn(Collections.emptyIterator());
        lenient().when(deletionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void insideATransaction_nothingIsDeletedBeforeTheCommit() {
        service.deleteMatchArtifacts(match());

        // Queued in the caller's transaction; a rollback would drop these rows and leave every file in place.
        verify(deletionRepository).saveAll(anyList());
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void afterTheCommit_theFilesAreDeletedAndTheRowsRemoved() {
        service.deleteMatchArtifacts(match());

        commit();

        ArgumentCaptor<DeleteObjectRequest> deleted = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client, org.mockito.Mockito.times(3)).deleteObject(deleted.capture());
        assertThat(deleted.getAllValues()).anyMatch(req -> "raw-videos".equals(req.bucket()));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> removed = ArgumentCaptor.forClass(List.class);
        verify(deletionRepository).deleteAllByIdInBatch(removed.capture());
        assertThat(removed.getValue()).hasSize(4); // raw video, tracking, HLS prefix, field image
    }

    @Test
    void aFailedDeleteStaysQueued() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("down").build())
                .thenReturn(null);

        service.deleteMatchArtifacts(match());
        commit();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> removed = ArgumentCaptor.forClass(List.class);
        verify(deletionRepository).deleteAllByIdInBatch(removed.capture());
        assertThat(removed.getValue()).hasSize(3);
    }

    @Test
    void retry_deletesTheQueuedRows() {
        TransactionSynchronizationManager.clearSynchronization();
        StorageDeletion row = new StorageDeletion();
        row.setId(UUID.randomUUID());
        row.setBucket("raw-videos");
        row.setObjectKey(matchId + ".mp4");
        when(deletionRepository.findTop500ByOrderByCreatedAtAsc()).thenReturn(List.of(row));

        assertThat(service.retryQueuedDeletions()).isEqualTo(1);

        verify(deletionRepository).deleteAllByIdInBatch(List.of(row.getId()));
        TransactionSynchronizationManager.initSynchronization();
    }

    @Test
    void aForgedWorkerUrlCannotReachAnotherBucket() {
        Match match = match();
        match.setTrackingDataUrl("http://minio:9000/medical-docs/someone.jpg");

        service.deleteMatchArtifacts(match);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StorageDeletion>> rows = ArgumentCaptor.forClass(List.class);
        verify(deletionRepository).saveAll(rows.capture());
        assertThat(rows.getValue()).noneMatch(row -> "medical-docs".equals(row.getBucket()));
        assertThat(rows.getValue()).anyMatch(row -> "tracking-data".equals(row.getBucket())
                && (matchId + ".json").equals(row.getObjectKey()));
    }

    private Match match() {
        Match match = new Match();
        match.setId(matchId);
        match.setSavedMinioFileName(matchId + ".mp4");
        return match;
    }

    private static void commit() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
    }
}
