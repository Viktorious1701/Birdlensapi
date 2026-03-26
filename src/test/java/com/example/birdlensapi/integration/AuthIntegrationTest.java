package com.example.birdlensapi.integration;

import com.example.birdlensapi.domain.user.RegisterRequest;
import com.example.birdlensapi.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebTestClient client;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void shouldRegisterUserSuccessfully() {
        RegisterRequest request = new RegisterRequest("test@example.com", "testuser", "securepass123");

        client.post().uri("/api/v1/auth/register")
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("\"success\":true");
                    assertThat(body).contains("test@example.com");
                    assertThat(body).contains("testuser");
                    assertThat(body).doesNotContain("securepass123");
                });

        assertThat(userRepository.findAll()).hasSize(1);
    }

    @Test
    void shouldRejectDuplicateEmail() {
        RegisterRequest request1 = new RegisterRequest("duplicate@example.com", "user1", "securepass123");
        client.post().uri("/api/v1/auth/register")
                .bodyValue(request1)
                .exchange()
                .expectStatus().isCreated();

        RegisterRequest request2 = new RegisterRequest("duplicate@example.com", "user2", "securepass123");
        client.post().uri("/api/v1/auth/register")
                .bodyValue(request2)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Email is already in use"));

        assertThat(userRepository.findAll()).hasSize(1);
    }

    @Test
    void shouldRejectValidationErrors() {
        RegisterRequest request = new RegisterRequest("invalid-email", "us", "pass");

        client.post().uri("/api/v1/auth/register")
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("VALIDATION_ERROR"));
    }
}