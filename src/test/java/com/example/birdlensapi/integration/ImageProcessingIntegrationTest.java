package com.example.birdlensapi.integration;

import com.example.birdlensapi.domain.post.Post;
import com.example.birdlensapi.domain.post.PostMedia;
import com.example.birdlensapi.domain.post.PostRepository;
import com.example.birdlensapi.domain.post.ProcessingStatus;
import com.example.birdlensapi.domain.post.PostType;
import com.example.birdlensapi.domain.post.PrivacyLevel;
import com.example.birdlensapi.domain.user.User;
import com.example.birdlensapi.domain.user.UserRepository;
import com.example.birdlensapi.messaging.events.PostCreatedEvent;
import com.example.birdlensapi.storage.S3StorageService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// The critical line: we activate BOTH the test profile AND the worker profile so the RabbitListener wakes up
@ActiveProfiles({"test", "worker"})
class ImageProcessingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private S3StorageService s3StorageService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        postRepository.deleteAll();
        userRepository.deleteAll();

        // Create the bucket in the Testcontainer dynamically using the raw S3Client
        try (S3Client s3Client = S3Client.builder()
                .endpointOverride(URI.create(minio.getS3URL()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(minio.getUserName(), minio.getPassword())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {

            try {
                s3Client.createBucket(b -> b.bucket("birdlens-media"));
            } catch (Exception ignored) {}
        }
    }

    @Test
    void shouldProcessImageAsyncAndSetStatusToCompleted() throws Exception {
        // 1. Create a minimal User
        User user = new User("worker@example.com", "worker", "pass");
        user = userRepository.save(user);

        // 2. Upload a valid, real mock image to S3 (Thumbnailator will crash if we just send random bytes)
        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        byte[] imageBytes = baos.toByteArray();

        String objectKey = "users/" + user.getId() + "/eagle.jpg";
        s3StorageService.uploadFile(objectKey, imageBytes, "image/jpeg");

        // 3. Create a Post in the database mapped with PENDING media
        Post post = new Post();
        post.setUser(user);
        post.setContent("Test post for image worker");
        post.setType(PostType.SIGHTING);
        post.setPrivacyLevel(PrivacyLevel.PUBLIC);

        PostMedia media = new PostMedia();
        media.setOriginalUrl(objectKey);
        media.setProcessingStatus(ProcessingStatus.PENDING);
        post.addMedia(media);

        post = postRepository.save(post);

        // 4. Publish Event to RabbitMQ
        rabbitTemplate.convertAndSend("posts.exchange", "post.created", new PostCreatedEvent(post.getId()));

        // 5. Use Awaitility to monitor the database because the Worker is processing in a separate thread
        Post finalPost = post;
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Post updatedPost = postRepository.findById(finalPost.getId()).orElseThrow();
                    PostMedia updatedMedia = updatedPost.getMedia().get(0);

                    assertThat(updatedMedia.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
                    assertThat(updatedMedia.getThumbnailUrl()).isNotNull().contains("_thumb.jpg");
                    assertThat(updatedMedia.getCompressedUrl()).isNotNull().contains("_comp.jpg");
                });
    }
}