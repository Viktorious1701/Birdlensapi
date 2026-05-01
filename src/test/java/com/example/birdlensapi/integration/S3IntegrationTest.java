package com.example.birdlensapi.integration;

import com.example.birdlensapi.domain.auth.LoginRequest;
import com.example.birdlensapi.domain.post.dto.PresignedUrlRequest;
import com.example.birdlensapi.domain.post.dto.PresignedUrlRequestItem;
import com.example.birdlensapi.domain.user.RegisterRequest;
import com.example.birdlensapi.domain.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class S3IntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebTestClient client;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.s3.endpoint}")
    private String s3Endpoint;

    @Value("${app.s3.access-key}")
    private String s3AccessKey;

    @Value("${app.s3.secret-key}")
    private String s3SecretKey;

    private String validJwtToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        // Register and login to acquire a valid JWT
        RegisterRequest register = new RegisterRequest("s3user@example.com", "s3user", "securepass123");
        client.post().uri("/api/v1/auth/register")
                .bodyValue(register)
                .exchange()
                .expectStatus().isCreated();

        LoginRequest login = new LoginRequest("s3user@example.com", "securepass123");
        validJwtToken = client.post().uri("/api/v1/auth/login")
                .bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .returnResult(JsonNode.class)
                .getResponseBody()
                .blockFirst()
                .get("data").get("accessToken").asText();

        // Create the bucket in the Testcontainer dynamically using the raw S3Client
        try (S3Client s3Client = S3Client.builder()
                .endpointOverride(URI.create(s3Endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(s3AccessKey, s3SecretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {

            try {
                s3Client.createBucket(b -> b.bucket("birdlens-media"));
            } catch (Exception ignored) {
                // Bucket already exists, ignore
            }
        }
    }

    @Test
    void shouldGeneratePresignedUrlAndUploadFile() {
        // 1. Request the pre-signed URL from our API
        PresignedUrlRequest request = new PresignedUrlRequest(List.of(
                new PresignedUrlRequestItem("test-bird.jpg", "image/jpeg")
        ));

        String presignedUrl = client.post().uri("/api/v1/posts/media/request-upload")
                .header("Authorization", "Bearer " + validJwtToken)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody()
                .get("data").get(0).get("presignedUrl").asText();

        assertThat(presignedUrl).isNotBlank();
        assertThat(presignedUrl).contains("X-Amz-Signature");

        // 2. Use a raw WebClient to simulate the Android app executing the PUT request directly to MinIO
        WebClient directS3Client = WebClient.create();
        byte[] dummyImageBytes = "dummy image content".getBytes();

        directS3Client.put()
                .uri(presignedUrl)
                .contentType(MediaType.IMAGE_JPEG)
                .bodyValue(dummyImageBytes)
                .exchange()
                .block();
    }
}