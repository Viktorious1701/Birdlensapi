package com.example.birdlensapi.storage;

import com.example.birdlensapi.domain.post.dto.PresignedUrlRequestItem;
import com.example.birdlensapi.domain.post.dto.PresignedUrlResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class S3StorageService {

    private final S3Presigner s3Presigner;
    private final String bucketName;

    public S3StorageService(S3Presigner s3Presigner, @Value("${app.s3.bucket}") String bucketName) {
        this.s3Presigner = s3Presigner;
        this.bucketName = bucketName;
    }

    public List<PresignedUrlResponse> generatePresignedUploadUrls(String userId, List<PresignedUrlRequestItem> requests) {
        return requests.stream().map(req -> {
            // Sanitize filename and prepend user ID and UUID to prevent collisions
            String sanitizedFilename = req.filename().replaceAll("[^a-zA-Z0-9.-]", "_");
            String objectKey = "users/" + userId + "/" + UUID.randomUUID() + "-" + sanitizedFilename;

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(req.contentType())
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(15))
                    .putObjectRequest(putObjectRequest)
                    .build();

            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

            return new PresignedUrlResponse(objectKey, presignedRequest.url().toString());
        }).collect(Collectors.toList());
    }
}