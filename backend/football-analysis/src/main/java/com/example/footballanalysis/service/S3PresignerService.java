package com.example.footballanalysis.service;

import com.example.footballanalysis.exception.ExternalServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Service
@Slf4j
public class S3PresignerService {

    private final S3Presigner s3Presigner;

    @Value("${minio.buckets.raw-videos}")
    private String rawVideoBucket;

    @Value("${minio.buckets.clips}")
    private String clipsBucket;

    public S3PresignerService(S3Presigner s3Presigner) {
        this.s3Presigner = s3Presigner;
    }

    public String generateUploadUrl(String fileName) {
        return generateUploadUrl(rawVideoBucket, fileName);
    }

    public String generateClipUploadUrl(String fileName) {
        return generateUploadUrl(clipsBucket, fileName);
    }

    public String generateUploadUrl(String bucketName, String fileName) {
        return generateUploadUrl(bucketName, fileName, "video/mp4");
    }

    private String generateUploadUrl(String bucketName, String fileName, String contentType) {
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

            log.debug("Pre-signed URL successfully generated for file: {}", fileName);
            return s3Presigner.presignPutObject(presignRequest).url().toString();
        } catch (Exception ex) {
            throw new ExternalServiceException("Nem sikerült feltöltési URL-t generálni a videóhoz.", ex);
        }
    }
}