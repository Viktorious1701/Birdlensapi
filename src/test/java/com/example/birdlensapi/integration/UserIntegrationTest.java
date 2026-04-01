package com.example.birdlensapi.integration;

import com.example.birdlensapi.domain.auth.LoginRequest;
import com.example.birdlensapi.domain.user.RegisterRequest;
import com.example.birdlensapi.domain.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

class UserIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebTestClient client;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void shouldRetrieveUserProfileSuccessfully() {
        // 1. Register a new user
        RegisterRequest register = new RegisterRequest("profile@example.com", "profileuser", "securepass123");
        client.post().uri("/api/v1/auth/register")
                .bodyValue(register)
                .exchange()
                .expectStatus().isCreated();

        // 2. Login to get token
        LoginRequest login = new LoginRequest("profile@example.com", "securepass123");
        String token = client.post().uri("/api/v1/auth/login")
                .bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .returnResult(JsonNode.class)
                .getResponseBody()
                .blockFirst()
                .get("data").get("accessToken").asText();

        // 3. Request profile with token
        client.get().uri("/api/v1/users/me")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.email").isEqualTo("profile@example.com")
                .jsonPath("$.data.username").isEqualTo("profileuser")
                .jsonPath("$.data.id").exists();
    }

    @Test
    void shouldReturn401WhenNoTokenProvided() {
        client.get().uri("/api/v1/users/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}