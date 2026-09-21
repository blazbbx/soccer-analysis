package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ExternalServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3PresignerService")
class S3PresignerServiceTest {

    @Mock private S3Presigner publicPresigner;
    @Mock private S3Presigner internalPresigner;
    @Mock private PresignedPutObjectRequest presignedPut;
    @Mock private PresignedGetObjectRequest presignedGet;

    private S3PresignerService service;

    @BeforeEach
    void setUp() {
        service = new S3PresignerService(publicPresigner, internalPresigner);
        ReflectionTestUtils.setField(service, "rawVideoBucket", "raw-videos");
        ReflectionTestUtils.setField(service, "clipsBucket", "clips");
    }

    @Test
    void generateClipUploadUrl_usesPublicPresignerAndClipsBucket() throws Exception {
        when(presignedPut.url()).thenReturn(URI.create("http://localhost:9000/clips/a.webm").toURL());
        when(publicPresigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedPut);

        assertThat(service.generateClipUploadUrl("a.webm", "video/webm"))
                .isEqualTo("http://localhost:9000/clips/a.webm");
        verify(publicPresigner).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    void generateInternalRawVideoDownloadUrl_usesInternalPresigner() throws Exception {
        when(presignedGet.url()).thenReturn(URI.create("http://minio:9000/raw-videos/game.mp4").toURL());
        when(internalPresigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGet);

        assertThat(service.generateInternalRawVideoDownloadUrl("game.mp4", Duration.ofMinutes(30)))
                .isEqualTo("http://minio:9000/raw-videos/game.mp4");
        verify(internalPresigner).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void generateDownloadUrl_usesPublicPresigner() throws Exception {
        when(presignedGet.url()).thenReturn(URI.create("http://localhost:9000/clips/out.mp4").toURL());
        when(publicPresigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGet);

        assertThat(service.generateDownloadUrl("clips", "out.mp4", Duration.ofMinutes(10)))
                .isEqualTo("http://localhost:9000/clips/out.mp4");
    }

    @Test
    void generateUploadUrl_wrapsSdkFailure() {
        when(publicPresigner.presignPutObject(any(PutObjectPresignRequest.class))).thenThrow(new RuntimeException("minio down"));

        assertThatThrownBy(() -> service.generateUploadUrl("file.mp4"))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void generateDownloadUrl_wrapsSdkFailure() {
        when(publicPresigner.presignGetObject(any(GetObjectPresignRequest.class))).thenThrow(new RuntimeException("minio down"));

        assertThatThrownBy(() -> service.generateDownloadUrl("clips", "x.mp4", Duration.ofMinutes(1)))
                .isInstanceOf(ExternalServiceException.class);
    }
}
