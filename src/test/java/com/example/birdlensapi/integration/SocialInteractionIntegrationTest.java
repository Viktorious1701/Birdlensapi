package com.example.birdlensapi.integration;

import com.example.birdlensapi.domain.auth.LoginRequest;
import com.example.birdlensapi.domain.post.Post;
import com.example.birdlensapi.domain.post.PostReactionRepository;
import com.example.birdlensapi.domain.post.PostRepository;
import com.example.birdlensapi.domain.post.PostType;
import com.example.birdlensapi.domain.post.PrivacyLevel;
import com.example.birdlensapi.domain.post.dto.CommentRequest;
import com.example.birdlensapi.domain.user.RegisterRequest;
import com.example.birdlensapi.domain.user.User;
import com.example.birdlensapi.domain.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

class SocialInteractionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebTestClient client;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostReactionRepository postReactionRepository;

    @Autowired
    private UserRepository userRepository;

    private String validJwtToken;
    private Post testPost;

    @BeforeEach
    void setUp() {
        postReactionRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Setup User and Auth
        RegisterRequest register = new RegisterRequest("social@example.com", "socialuser", "securepass123");
        client.post().uri("/api/v1/auth/register")
                .bodyValue(register)
                .exchange()
                .expectStatus().isCreated();

        LoginRequest login = new LoginRequest("social@example.com", "securepass123");
        validJwtToken = client.post().uri("/api/v1/auth/login")
                .bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .returnResult(JsonNode.class)
                .getResponseBody()
                .blockFirst()
                .get("data").get("accessToken").asText();

        User user = userRepository.findByEmail("social@example.com").orElseThrow();

        // 2. Seed a Post
        Post post = new Post();
        post.setUser(user);
        post.setContent("This is a social test post!");
        post.setType(PostType.GENERAL);
        post.setPrivacyLevel(PrivacyLevel.PUBLIC);
        testPost = postRepository.save(post);
    }

    @Test
    void shouldToggleLikeSuccessfully() {
        // 1. Initial state: 0 reactions
        assertThat(postReactionRepository.count()).isEqualTo(0);

        // 2. First toggle: Like the post
        client.post().uri("/api/v1/posts/" + testPost.getId() + "/reactions")
                .header("Authorization", "Bearer " + validJwtToken)
                .exchange()
                .expectStatus().isOk();

        assertThat(postReactionRepository.count()).isEqualTo(1);

        // 3. Second toggle: Unlike the post
        client.post().uri("/api/v1/posts/" + testPost.getId() + "/reactions")
                .header("Authorization", "Bearer " + validJwtToken)
                .exchange()
                .expectStatus().isOk();

        assertThat(postReactionRepository.count()).isEqualTo(0);
    }

    @Test
    void shouldAddCommentAndPaginateResults() {
        // 1. Add 3 comments
        for (int i = 1; i <= 3; i++) {
            CommentRequest request = new CommentRequest("Comment number " + i);

            client.post().uri("/api/v1/posts/" + testPost.getId() + "/comments")
                    .header("Authorization", "Bearer " + validJwtToken)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.data.content").isEqualTo("Comment number " + i)
                    .jsonPath("$.data.user.username").isEqualTo("socialuser"); // Validates AC: include username
        }

        // 2. Fetch comments with pagination (size 2)
        client.get().uri("/api/v1/posts/" + testPost.getId() + "/comments?page=0&size=2")
                .header("Authorization", "Bearer " + validJwtToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.content.length()").isEqualTo(2)
                .jsonPath("$.data.totalElements").isEqualTo(3)
                .jsonPath("$.data.totalPages").isEqualTo(2)
                .jsonPath("$.data.content[0].content").isEqualTo("Comment number 1"); // Verify default sort is ASC for comments
    }

    @Test
    void shouldReturn404WhenInteractingWithNonExistentPost() {
        String fakeId = "00000000-0000-0000-0000-000000000000";

        client.post().uri("/api/v1/posts/" + fakeId + "/reactions")
                .header("Authorization", "Bearer " + validJwtToken)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("RESOURCE_NOT_FOUND");
    }
}