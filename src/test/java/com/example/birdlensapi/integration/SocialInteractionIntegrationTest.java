package com.example.birdlensapi.integration;

import com.example.birdlensapi.config.RabbitMQConfig;
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
import com.example.birdlensapi.messaging.events.NewCommentEvent;
import com.example.birdlensapi.messaging.events.PostLikedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private String validJwtToken;
    private User testUser;
    private Post testPost;

    @BeforeEach
    void setUp() {
        postReactionRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Setup Original Post Owner
        User owner = new User("owner@example.com", "owneruser", "pass");
        owner = userRepository.save(owner);

        Post post = new Post();
        post.setUser(owner);
        post.setContent("This is an original post from the owner!");
        post.setType(PostType.GENERAL);
        post.setPrivacyLevel(PrivacyLevel.PUBLIC);
        testPost = postRepository.save(post);

        // 2. Setup the User interacting with the post
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

        testUser = userRepository.findByEmail("social@example.com").orElseThrow();
    }

    @Test
    void shouldToggleLikeSuccessfullyAndPublishEvent() {
        // 1. Initial state: 0 reactions
        assertThat(postReactionRepository.count()).isEqualTo(0);

        // 2. First toggle: Like the post
        client.post().uri("/api/v1/posts/" + testPost.getId() + "/reactions")
                .header("Authorization", "Bearer " + validJwtToken)
                .exchange()
                .expectStatus().isOk();

        assertThat(postReactionRepository.count()).isEqualTo(1);

        // Verify RabbitMQ Event published correctly since it's a NEW like
        PostLikedEvent event = (PostLikedEvent) rabbitTemplate.receiveAndConvert(
                RabbitMQConfig.NOTIFICATIONS_QUEUE, 2000);

        assertThat(event).isNotNull();
        assertThat(event.likerUserId()).isEqualTo(testUser.getId());
        assertThat(event.postOwnerUserId()).isEqualTo(testPost.getUser().getId());

        // 3. Second toggle: Unlike the post
        client.post().uri("/api/v1/posts/" + testPost.getId() + "/reactions")
                .header("Authorization", "Bearer " + validJwtToken)
                .exchange()
                .expectStatus().isOk();

        assertThat(postReactionRepository.count()).isEqualTo(0);

        // Verify NO event is published for an 'unlike' action
        PostLikedEvent unlikeEvent = (PostLikedEvent) rabbitTemplate.receiveAndConvert(
                RabbitMQConfig.NOTIFICATIONS_QUEUE, 500); // short wait, should be empty
        assertThat(unlikeEvent).isNull();
    }

    @Test
    void shouldAddCommentAndPublishEvent() {
        String longComment = "This is a really fantastic bird sighting and I am so glad you shared it with the entire community here today!";
        CommentRequest request = new CommentRequest(longComment);

        client.post().uri("/api/v1/posts/" + testPost.getId() + "/comments")
                .header("Authorization", "Bearer " + validJwtToken)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated();

        // Verify RabbitMQ Event published with truncated snippet logic applied
        NewCommentEvent event = (NewCommentEvent) rabbitTemplate.receiveAndConvert(
                RabbitMQConfig.NOTIFICATIONS_QUEUE, 2000);

        assertThat(event).isNotNull();
        assertThat(event.commenterUserId()).isEqualTo(testUser.getId());
        // Snippet length check: "This is a really fantastic bird sighting and I a..."
        assertThat(event.commentSnippet()).hasSize(50);
        assertThat(event.commentSnippet()).endsWith("...");
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