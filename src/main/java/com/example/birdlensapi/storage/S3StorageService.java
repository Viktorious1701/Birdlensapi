package com.example.birdlensapi.storage;

import com.example.birdlensapi.domain.post.dto.PresignedUrlRequestItem;
import com.example.birdlensapi.domain.post.dto.PresignedUrlResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class S3StorageService {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final String bucketName;

    public S3StorageService(S3Presigner s3Presigner, S3Client s3Client, @Value("${app.s3.bucket}") String bucketName) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    public List<PresignedUrlResponse> generatePresignedUploadUrls(String userId, List<PresignedUrlRequestItem> requests) {
        return requests.stream().map(req -> {
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

    // Opens a direct input stream to MinIO to prevent loading huge files into server RAM all at once
    public InputStream downloadFileStream(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        return s3Client.getObject(getObjectRequest);
    }

    public void uploadFile(String key, byte[] content, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();
        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content));
    }
}