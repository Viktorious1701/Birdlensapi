package com.example.birdlensapi.integration;

import com.example.birdlensapi.domain.auth.LoginRequest;
import com.example.birdlensapi.domain.subscription.Subscription;
import com.example.birdlensapi.domain.subscription.SubscriptionRepository;
import com.example.birdlensapi.domain.user.RegisterRequest;
import com.example.birdlensapi.domain.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.util.List;

class SubscriptionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebTestClient client;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    private String validJwtToken;

    @BeforeEach
    void setUp() {
        // Clear users first to avoid foreign key violations, then subscriptions
        userRepository.deleteAll();
        subscriptionRepository.deleteAll();

        // 1. Setup Standard User Auth
        RegisterRequest register = new RegisterRequest("subuser@example.com", "subuser", "securepass123");
        client.post().uri("/api/v1/auth/register")
                .bodyValue(register)
                .exchange()
                .expectStatus().isCreated();

        LoginRequest login = new LoginRequest("subuser@example.com", "securepass123");
        validJwtToken = client.post().uri("/api/v1/auth/login")
                .bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .returnResult(JsonNode.class)
                .getResponseBody()
                .blockFirst()
                .get("data").get("accessToken").asText();

        // 2. Seed database with test Subscriptions
        Subscription sub1 = new Subscription();
        sub1.setName("ExBird Premium (Monthly)");
        sub1.setDescription("Full access to analytical visiting times for 30 days.");
        sub1.setPrice(new BigDecimal("9.99"));
        sub1.setDurationDays(30);
        sub1.setProductId("sub_premium_monthly");

        Subscription sub2 = new Subscription();
        sub2.setName("ExBird Pro (Annual)");
        sub2.setDescription("Yearly access to analytical visiting times.");
        sub2.setPrice(new BigDecimal("99.99"));
        sub2.setDurationDays(365);
        sub2.setProductId("sub_premium_annual");

        subscriptionRepository.saveAll(List.of(sub1, sub2));
    }

    @Test
    void shouldReturn401WhenNoTokenProvided() {
        client.get().uri("/api/v1/subscriptions")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldFetchSubscriptionsCorrectlyForStandardUser() {
        client.get().uri("/api/v1/subscriptions")
                .header("Authorization", "Bearer " + validJwtToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.length()").isEqualTo(2)
                .jsonPath("$.data[0].name").isEqualTo("ExBird Premium (Monthly)")
                .jsonPath("$.data[0].price").isEqualTo(9.99) // Validating price renders as a standard numeric JSON node
                .jsonPath("$.data[0].productId").isEqualTo("sub_premium_monthly")
                .jsonPath("$.data[1].productId").isEqualTo("sub_premium_annual");
    }
}