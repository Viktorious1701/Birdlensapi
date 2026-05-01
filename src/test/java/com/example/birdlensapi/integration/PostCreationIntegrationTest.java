package com.example.birdlensapi.integration;

import com.example.birdlensapi.config.RabbitMQConfig;
import com.example.birdlensapi.domain.auth.LoginRequest;
import com.example.birdlensapi.domain.post.PostType;
import com.example.birdlensapi.domain.post.PrivacyLevel;
import com.example.birdlensapi.domain.post.dto.CreatePostRequest;
import com.example.birdlensapi.domain.taxonomy.BirdTaxonomy;
import com.example.birdlensapi.domain.taxonomy.TaxonomyRepository;
import com.example.birdlensapi.domain.user.RegisterRequest;
import com.example.birdlensapi.domain.user.UserRepository;
import com.example.birdlensapi.messaging.events.PostCreatedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PostCreationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebTestClient client;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaxonomyRepository taxonomyRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private String validJwtToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        taxonomyRepository.deleteAll();

        // 1. Setup Auth
        RegisterRequest register = new RegisterRequest("postuser@example.com", "postuser", "securepass123");
        client.post().uri("/api/v1/auth/register")
                .bodyValue(register)
                .exchange()
                .expectStatus().isCreated();

        LoginRequest login = new LoginRequest("postuser@example.com", "securepass123");
        validJwtToken = client.post().uri("/api/v1/auth/login")
                .bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .returnResult(JsonNode.class)
                .getResponseBody()
                .blockFirst()
                .get("data").get("accessToken").asText();

        // 2. Seed a taxonomy record to tag
        BirdTaxonomy taxonomy = new BirdTaxonomy();
        taxonomy.setSpeciesCode("baleag");
        taxonomy.setCommonName("Bald Eagle");
        taxonomy.setScientificName("Haliaeetus leucocephalus");
        taxonomy.setTaxonOrder(BigDecimal.valueOf(1.0));
        taxonomyRepository.save(taxonomy);
    }

    @Test
    void shouldCreatePostSuccessfullyAndPublishRabbitMQEvent() {
        // Arrange
        CreatePostRequest request = new CreatePostRequest(
                "Spotted a majestic eagle!",
                "Tao Dan Park",
                10.7769, 106.7009,
                PrivacyLevel.PUBLIC,
                PostType.SIGHTING,
                LocalDate.now(),
                "baleag",
                List.of("users/postuser/uuid-1-image.jpg", "users/postuser/uuid-2-image.jpg")
        );

        // Act & Assert 1: Database Creation and 201 Response
        String createdPostIdStr = client.post().uri("/api/v1/posts")
                .header("Authorization", "Bearer " + validJwtToken)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated() // Verifies HTTP 201 Created
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody()
                .get("data").get("id").asText();

        UUID createdPostId = UUID.fromString(createdPostIdStr);
        assertThat(createdPostId).isNotNull();

        // Act & Assert 2: RabbitMQ Publish Verification
        // The RabbitTemplate connects to the Testcontainer RabbitMQ instance.
        // We wait up to 2 seconds for the message to arrive in the queue.
        PostCreatedEvent event = (PostCreatedEvent) rabbitTemplate.receiveAndConvert(
                RabbitMQConfig.IMAGE_PROCESSING_QUEUE, 2000);

        assertThat(event).isNotNull();
        assertThat(event.postId()).isEqualTo(createdPostId);
    }
}