package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ExternalServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Service
@Slf4j
public class S3PresignerService {

    private final S3Presigner s3Presigner;
    private final S3Presigner internalS3Presigner;

    @Value("${minio.buckets.raw-videos}")
    private String rawVideoBucket;

    @Value("${minio.buckets.clips}")
    private String clipsBucket;

    public S3PresignerService(S3Presigner s3Presigner,
                              @Qualifier("internalS3Presigner") S3Presigner internalS3Presigner) {
        this.s3Presigner = s3Presigner;
        this.internalS3Presigner = internalS3Presigner;
    }

    /**
     * Presigned GET URL signed for the docker-internal MinIO host.
     * Use when handing the URL to a worker that runs on the same docker network.
     */
    public String generateInternalRawVideoDownloadUrl(String fileName, Duration duration) {
        try {
            GetObjectRequest objectRequest = GetObjectRequest.builder()
                    .bucket(rawVideoBucket)
                    .key(fileName)
                    .responseContentType("video/mp4")
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(duration)
                    .getObjectRequest(objectRequest)
                    .build();

            return internalS3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (Exception ex) {
            throw new ExternalServiceException("Nem sikerült belső letöltési URL-t generálni a videóhoz.", ex);
        }
    }

    public String generateUploadUrl(String fileName) {
        return generateUploadUrl(rawVideoBucket, fileName);
    }

    public String generateClipUploadUrl(String fileName) {
        return generateUploadUrl(clipsBucket, fileName);
    }

    public String generateClipUploadUrl(String fileName, String contentType) {
        return generateUploadUrl(clipsBucket, fileName, contentType);
    }

    public String generateUploadUrl(String bucketName, String fileName) {
        return generateUploadUrl(bucketName, fileName, "video/mp4");
    }

    public String generateUploadUrl(String bucketName, String fileName, String contentType) {
        log.debug("Generating pre-signed upload URL for file: {} in bucket: {}", fileName, bucketName);
        try {
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(15)) // Valid for 15 minutes
                    .putObjectRequest(objectRequest)
                    .build();

            String presignedUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

            log.debug("Pre-signed URL successfully generated for file: {}", fileName);
            return presignedUrl; 
        } catch (Exception ex) {
            throw new ExternalServiceException("Nem sikerült feltöltési URL-t generálni a videóhoz.", ex);
        }
    }

    public String generateClipDownloadUrl(String fileName) {
        return generateDownloadUrl(clipsBucket, fileName, Duration.ofMinutes(10));
    }

    public String generateDownloadUrl(String bucketName, String fileName, Duration duration) {
        log.debug("Generating pre-signed download URL for file: {} in bucket: {}", fileName, bucketName);
        try {
            GetObjectRequest objectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .responseContentType("video/mp4") 
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(duration)
                    .getObjectRequest(objectRequest)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (Exception ex) {
            throw new ExternalServiceException("Nem sikerült letöltési URL-t generálni a videóhoz.", ex);
        }
    }
}