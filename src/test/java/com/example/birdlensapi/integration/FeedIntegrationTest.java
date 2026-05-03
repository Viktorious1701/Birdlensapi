package com.example.birdlensapi.integration;

import com.example.birdlensapi.domain.auth.LoginRequest;
import com.example.birdlensapi.domain.post.Post;
import com.example.birdlensapi.domain.post.PostMedia;
import com.example.birdlensapi.domain.post.PostMediaRepository;
import com.example.birdlensapi.domain.post.PostRepository;
import com.example.birdlensapi.domain.post.PostType;
import com.example.birdlensapi.domain.post.PrivacyLevel;
import com.example.birdlensapi.domain.post.ProcessingStatus;
import com.example.birdlensapi.domain.user.RegisterRequest;
import com.example.birdlensapi.domain.user.User;
import com.example.birdlensapi.domain.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

class FeedIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebTestClient client;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostMediaRepository postMediaRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CacheManager cacheManager;

    private String validJwtToken;
    private User testUser;

    @BeforeEach
    void setUp() {
        postRepository.deleteAll();
        userRepository.deleteAll();

        // Evict any lingering cache data from previous runs
        if (cacheManager.getCache("feed") != null) {
            cacheManager.getCache("feed").clear();
        }

        // 1. Setup Auth and User Profile
        RegisterRequest register = new RegisterRequest("feeduser@example.com", "feeduser", "securepass123");
        client.post().uri("/api/v1/auth/register")
                .bodyValue(register)
                .exchange()
                .expectStatus().isCreated();

        testUser = userRepository.findByEmail("feeduser@example.com").orElseThrow();

        LoginRequest login = new LoginRequest("feeduser@example.com", "securepass123");
        validJwtToken = client.post().uri("/api/v1/auth/login")
                .bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .returnResult(JsonNode.class)
                .getResponseBody()
                .blockFirst()
                .get("data").get("accessToken").asText();
    }

    @Test
    void shouldExcludePendingPostsAndIncludeCompletedPosts() {
        // 1. Create a post with NO media (Should appear in feed immediately)
        Post noMediaPost = new Post();
        noMediaPost.setUser(testUser);
        noMediaPost.setContent("No media post");
        noMediaPost.setType(PostType.GENERAL);
        noMediaPost.setPrivacyLevel(PrivacyLevel.PUBLIC);
        postRepository.save(noMediaPost);

        // 2. Create a post with PENDING media (Should be hidden from feed)
        Post pendingPost = new Post();
        pendingPost.setUser(testUser);
        pendingPost.setContent("Pending media post");
        pendingPost.setType(PostType.SIGHTING);
        pendingPost.setPrivacyLevel(PrivacyLevel.PUBLIC);

        PostMedia pendingMedia = new PostMedia();
        pendingMedia.setOriginalUrl("s3://test/pending.jpg");
        pendingMedia.setProcessingStatus(ProcessingStatus.PENDING);
        pendingPost.addMedia(pendingMedia);

        pendingPost = postRepository.save(pendingPost);

        // 3. Fetch feed -> Expect ONLY the no-media post
        client.get().uri("/api/v1/posts?page=0&size=10")
                .header("Authorization", "Bearer " + validJwtToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.content.length()").isEqualTo(1)
                .jsonPath("$.data.content[0].content").isEqualTo("No media post");

        // 4. Simulate Background Worker completing the media processing
        PostMedia savedPendingMedia = postRepository.findById(pendingPost.getId()).get().getMedia().get(0);
        savedPendingMedia.setProcessingStatus(ProcessingStatus.COMPLETED);
        savedPendingMedia.setThumbnailUrl("s3://test/thumb.jpg");
        savedPendingMedia.setCompressedUrl("s3://test/comp.jpg");
        postMediaRepository.save(savedPendingMedia);

        // Clear the cache manually because we are overriding time in this test
        cacheManager.getCache("feed").clear();

        // 5. Fetch feed -> Expect BOTH posts to now appear
        client.get().uri("/api/v1/posts?page=0&size=10")
                .header("Authorization", "Bearer " + validJwtToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.content.length()").isEqualTo(2)
                // Verify the newly completed media contains the processed URLs, not the original URL
                .jsonPath("$.data.content[0].media[0].thumbnailUrl").isEqualTo("s3://test/thumb.jpg")
                .jsonPath("$.data.content[0].media[0].originalUrl").doesNotExist();
    }
}