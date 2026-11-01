package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ExternalServiceException;
import com.example.footballanalysis.model.db.MedicalFileDeletion;
import com.example.footballanalysis.repository.MedicalFileDeletionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalFileStoreTest {

    private static final String KEY = "medical/p/c";

    @Mock private S3Client s3Client;
    @Mock private S3PresignerService presignerService;
    @Mock private MedicalFileDeletionRepository deletionRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private MedicalFileStore store() {
        return new MedicalFileStore(s3Client, presignerService, "medical-docs", deletionRepository,
                Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC), transactionManager);
    }

    private static MedicalFileDeletion queued(String key) {
        MedicalFileDeletion row = new MedicalFileDeletion();
        row.setId(UUID.randomUUID());
        row.setObjectKey(key);
        row.setCertificateId(UUID.randomUUID());
        return row;
    }

    private void withTransaction(Runnable work) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            work.run();
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void aDeleteInATransactionIsQueuedFirstAndForgottenOnlyAfterTheCommitDeletedIt() {
        when(deletionRepository.saveAll(any())).thenAnswer(call -> call.getArgument(0));

        withTransaction(() -> {
            store().deleteAfterCommit(KEY, UUID.randomUUID());

            verify(deletionRepository).saveAll(any());
            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));

            TransactionSynchronizationUtils.triggerAfterCommit();
        });

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        verify(deletionRepository).deleteAllByIdInBatch(any());
    }

    @Test
    void aFailedDeleteAfterCommitKeepsTheQueueRowAndDoesNotReachTheCaller() {
        when(deletionRepository.saveAll(any())).thenAnswer(call -> call.getArgument(0));
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(SdkClientException.create("down"));

        withTransaction(() -> {
            store().deleteAfterCommit(KEY, UUID.randomUUID());
            TransactionSynchronizationUtils.triggerAfterCommit();
        });

        verify(deletionRepository, never()).deleteAllByIdInBatch(any());
    }

    @Test
    void aRollbackDeletesNothing() {
        when(deletionRepository.saveAll(any())).thenAnswer(call -> call.getArgument(0));

        withTransaction(() -> {
            store().deleteAfterCommit(KEY, UUID.randomUUID());
            TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        });

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void aFailedDirectDeleteIsQueuedForTheRetry() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(503).build());

        store().delete(KEY, UUID.randomUUID());

        ArgumentCaptor<MedicalFileDeletion> saved = ArgumentCaptor.forClass(MedicalFileDeletion.class);
        verify(deletionRepository).save(saved.capture());
        assertThat(saved.getValue().getObjectKey()).isEqualTo(KEY);
    }

    @Test
    void theRetryForgetsOnlyTheFilesItDeleted() {
        MedicalFileDeletion good = queued("medical/p/good");
        MedicalFileDeletion bad = queued("medical/p/bad");
        when(deletionRepository.findAll()).thenReturn(List.of(good, bad));
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenAnswer(call -> {
            DeleteObjectRequest request = call.getArgument(0);
            if (request.key().equals(bad.getObjectKey())) {
                throw SdkClientException.create("down");
            }
            return DeleteObjectResponse.builder().build();
        });

        assertThat(store().retryQueuedDeletions()).isEqualTo(1);

        verify(deletionRepository).deleteAllByIdInBatch(List.of(good.getId()));
    }

    @Test
    void theDownloadUrlAsksForNoStore() {
        when(presignerService.generateDownloadUrl(any(), any(), any(), any(), any(), any())).thenReturn("http://minio/get");

        store().downloadUrl(KEY, "application/pdf");

        verify(presignerService).generateDownloadUrl(eq("medical-docs"), eq(KEY), eq(MedicalFileStore.DOWNLOAD_URL_TTL),
                eq("application/pdf"), eq("inline; filename=\"sportorvosi.pdf\""), eq("no-store, private"));
    }

    @Test
    void onlyA404CountsAsMissing() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(NoSuchKeyException.builder().statusCode(404).build());
        assertThat(store().size(KEY)).isEmpty();

        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        assertThat(store().size(KEY)).isEmpty();

        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().statusCode(404).build());
        assertThat(store().firstBytes(KEY, 8)).isEmpty();
    }

    @Test
    void aServerErrorOrTimeoutIsNotTakenForAMissingFile() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(503).build());
        assertThatThrownBy(() -> store().size(KEY)).isInstanceOf(ExternalServiceException.class);

        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(SdkClientException.create("timed out"));
        assertThatThrownBy(() -> store().size(KEY)).isInstanceOf(ExternalServiceException.class);

        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(500).build());
        assertThatThrownBy(() -> store().firstBytes(KEY, 8))
                .isInstanceOf(ExternalServiceException.class)
                .satisfies(ex -> assertThat(((ExternalServiceException) ex).getMessageCode())
                        .isEqualTo("error.medical.storage_unavailable"));
    }
}
