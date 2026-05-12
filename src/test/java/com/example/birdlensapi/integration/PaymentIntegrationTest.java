package com.example.birdlensapi.integration;

import com.example.birdlensapi.domain.auth.LoginRequest;
import com.example.birdlensapi.domain.payment.PendingPaymentContext;
import com.example.birdlensapi.domain.payment.dto.PaymentLinkRequest;
import com.example.birdlensapi.domain.subscription.Subscription;
import com.example.birdlensapi.domain.subscription.SubscriptionRepository;
import com.example.birdlensapi.domain.user.RegisterRequest;
import com.example.birdlensapi.domain.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentIntegrationTest extends AbstractIntegrationTest {

    private static MockWebServer mockPayOSServer;

    @Autowired
    private WebTestClient client;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private String validJwtToken;
    private UUID testSubscriptionId;

    @BeforeAll
    static void setupServer() throws IOException {
        mockPayOSServer = new MockWebServer();
        mockPayOSServer.start();
    }

    @AfterAll
    static void teardownServer() throws IOException {
        mockPayOSServer.shutdown();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.payos.base-url", () -> mockPayOSServer.url("/").toString());
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        subscriptionRepository.deleteAll();

        // Seed Subscription
        Subscription sub = new Subscription();
        sub.setName("ExBird Premium");
        sub.setPrice(new BigDecimal("50000"));
        sub = subscriptionRepository.save(sub);
        testSubscriptionId = sub.getId();

        // Seed User
        RegisterRequest register = new RegisterRequest("payos@example.com", "payosuser", "securepass123");
        client.post().uri("/api/v1/auth/register").bodyValue(register).exchange();

        LoginRequest login = new LoginRequest("payos@example.com", "securepass123");
        validJwtToken = client.post().uri("/api/v1/auth/login")
                .bodyValue(login)
                .exchange()
                .returnResult(JsonNode.class)
                .getResponseBody()
                .blockFirst()
                .get("data").get("accessToken").asText();
    }

    @Test
    void shouldReturn404WhenRequestingLinkForUnknownSubscription() {
        PaymentLinkRequest request = new PaymentLinkRequest(UUID.randomUUID());

        client.post().uri("/api/v1/payos/create-payment-link")
                .header("Authorization", "Bearer " + validJwtToken)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void shouldInitiatePaymentAndStoreContextInRedis() {
        // Mock a successful PayOS Response
        String mockResponse = """
                {
                  "code": "00",
                  "desc": "success",
                  "data": {
                    "checkoutUrl": "https://pay.payos.vn/web/mock-checkout",
                    "paymentLinkId": "abc123xyz"
                  }
                }
                """;

        mockPayOSServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(mockResponse));

        PaymentLinkRequest request = new PaymentLinkRequest(testSubscriptionId);

        String orderCodeStr = client.post().uri("/api/v1/payos/create-payment-link")
                .header("Authorization", "Bearer " + validJwtToken)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody()
                .get("data").get("orderCode").asText();

        // Verify the response contains the checkout URL
        client.post().uri("/api/v1/payos/create-payment-link")
                .header("Authorization", "Bearer " + validJwtToken)
                .bodyValue(request)
                .exchange()
                .expectBody()
                .jsonPath("$.data.checkoutUrl").isEqualTo("https://pay.payos.vn/web/mock-checkout");

        // Verify the context was securely saved into Redis
        String redisKey = "pending_payment:" + orderCodeStr;
        PendingPaymentContext context = (PendingPaymentContext) redisTemplate.opsForValue().get(redisKey);

        assertThat(context).isNotNull();
        assertThat(context.subscriptionId()).isEqualTo(testSubscriptionId);
    }
}