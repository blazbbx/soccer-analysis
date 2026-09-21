package com.example.footballanalysis.service;

import com.example.footballanalysis.model.db.Match;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioObjectCleanupServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private ListObjectsV2Iterable paginator;

    @Test
    void deleteMatchArtifacts_deletesRawVideoAndFieldDetectionJpeg() {
        when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenReturn(paginator);
        when(paginator.iterator()).thenReturn(Collections.emptyIterator());

        UUID matchId = UUID.fromString("2391e38e-5fd9-4b0a-94ac-54dc4eca3201");
        Match match = new Match();
        match.setId(matchId);
        match.setSavedMinioFileName(matchId + ".mp4");

        new MinioObjectCleanupService(
                s3Client, "raw-videos", "tracking-data", "hls-streams", "clips", "field-detection")
                .deleteMatchArtifacts(match);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client, org.mockito.Mockito.atLeast(2)).deleteObject(captor.capture());

        assertThat(captor.getAllValues())
                .anyMatch(req -> "raw-videos".equals(req.bucket()) && (matchId + ".mp4").equals(req.key()));
        assertThat(captor.getAllValues())
                .anyMatch(req -> "field-detection".equals(req.bucket()) && (matchId + ".jpg").equals(req.key()));
        assertThat(captor.getAllValues())
                .anyMatch(req -> "tracking-data".equals(req.bucket()) && (matchId + ".json").equals(req.key()));
    }
}
