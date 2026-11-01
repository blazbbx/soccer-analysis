package com.example.footballanalysis;

import com.example.footballanalysis.repository.MedicalFileDeletionRepository;
import com.example.footballanalysis.service.MedicalFileStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The deletion queue on a real PostgreSQL with real transactions: an after-commit callback sits on a finished
 * transaction, so only a run against a real database shows that the queue row really goes, or really stays.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MedicalFileDeletionQueueTest {

    private static final String KEY = "medical/profile/certificate";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private MedicalFileDeletionRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private S3Client s3;
    private MedicalFileStore store;
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        s3 = mock(S3Client.class);
        store = new MedicalFileStore(s3, null, "medical-docs", repository, Clock.systemUTC(), transactionManager);
        transaction = new TransactionTemplate(transactionManager);
    }

    @Test
    void aSuccessfulDeleteAfterCommitLeavesNoQueueRow() {
        doReturn(DeleteObjectResponse.builder().build()).when(s3).deleteObject(any(DeleteObjectRequest.class));

        transaction.executeWithoutResult(status -> store.deleteAfterCommit(KEY, UUID.randomUUID()));

        verify(s3).deleteObject(any(DeleteObjectRequest.class));
        assertThat(repository.count()).isZero();
    }

    @Test
    void aFailedDeleteStaysQueuedAndTheRetryFinishesIt() {
        doThrow(SdkClientException.create("down")).when(s3).deleteObject(any(DeleteObjectRequest.class));

        transaction.executeWithoutResult(status -> store.deleteAfterCommit(KEY, UUID.randomUUID()));

        assertThat(repository.findAll()).singleElement()
                .satisfies(row -> assertThat(row.getObjectKey()).isEqualTo(KEY));
        assertThat(store.retryQueuedDeletions()).isZero();
        assertThat(repository.count()).isEqualTo(1);

        doReturn(DeleteObjectResponse.builder().build()).when(s3).deleteObject(any(DeleteObjectRequest.class));
        assertThat(store.retryQueuedDeletions()).isEqualTo(1);
        assertThat(repository.count()).isZero();
        verify(s3, times(3)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void aRollbackDropsTheQueueRowAndKeepsTheFile() {
        transaction.executeWithoutResult(status -> {
            store.deleteAfterCommit(KEY, UUID.randomUUID());
            status.setRollbackOnly();
        });

        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
        assertThat(repository.count()).isZero();
    }

    @Test
    void aFailedDirectDeleteIsQueuedOutsideATransaction() {
        doThrow(SdkClientException.create("down")).when(s3).deleteObject(any(DeleteObjectRequest.class));

        store.delete(KEY, UUID.randomUUID());

        assertThat(repository.count()).isEqualTo(1);
    }
}
