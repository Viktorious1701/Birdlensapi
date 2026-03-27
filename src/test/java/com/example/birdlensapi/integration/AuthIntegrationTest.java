package com.example.birdlensapi.integration;

import com.example.birdlensapi.domain.user.RegisterRequest;
import com.example.birdlensapi.domain.auth.LoginRequest;
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

    // ── Register ──────────────────────────────────────────────────────────────

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
                    // Tokens are present in the response
                    assertThat(body).contains("accessToken");
                    assertThat(body).contains("refreshToken");
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

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    void shouldLoginSuccessfullyAndReturnTokens() {
        // Register first
        RegisterRequest register = new RegisterRequest("login@example.com", "loginuser", "securepass123");
        client.post().uri("/api/v1/auth/register")
                .bodyValue(register)
                .exchange()
                .expectStatus().isCreated();

        // Then login
        LoginRequest login = new LoginRequest("login@example.com", "securepass123");
        client.post().uri("/api/v1/auth/login")
                .bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("\"success\":true");
                    assertThat(body).contains("accessToken");
                    assertThat(body).contains("refreshToken");
                    // Password must never appear in response
                    assertThat(body).doesNotContain("securepass123");
                });
    }

    @Test
    void shouldReturn401OnInvalidCredentials() {
        // Register a user
        RegisterRequest register = new RegisterRequest("creds@example.com", "credsuser", "securepass123");
        client.post().uri("/api/v1/auth/register")
                .bodyValue(register)
                .exchange()
                .expectStatus().isCreated();

        // Login with wrong password
        LoginRequest login = new LoginRequest("creds@example.com", "wrongpassword");
        client.post().uri("/api/v1/auth/login")
                .bodyValue(login)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("INVALID_CREDENTIALS"));
    }

    @Test
    void shouldReturn401WhenAccessingProtectedEndpointWithoutToken() {
        client.get().uri("/api/v1/users/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}