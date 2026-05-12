package com.example.birdlensapi.integration;

import com.example.birdlensapi.config.RabbitMQConfig;
import com.example.birdlensapi.domain.payment.PendingPaymentContext;
import com.example.birdlensapi.domain.payment.dto.PayOSWebhookPayload;
import com.example.birdlensapi.domain.subscription.Subscription;
import com.example.birdlensapi.domain.subscription.SubscriptionRepository;
import com.example.birdlensapi.domain.user.User;
import com.example.birdlensapi.domain.user.UserRepository;
import com.example.birdlensapi.messaging.events.SubscriptionActivatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebTestClient client;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${app.payos.checksum-key}")
    private String checksumKey;

    @Value("${app.payos.return-url}")
    private String returnUrl;

    @Value("${app.payos.cancel-url}")
    private String cancelUrl;

    private User testUser;
    private Subscription testSubscription;
    private final long testOrderCode = 999888777666L;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        subscriptionRepository.deleteAll();

        // 1. Create a user (No Subscription Yet)
        testUser = new User("webhook@example.com", "webhookuser", "securepass123");
        testUser = userRepository.save(testUser);

        // 2. Create a Subscription
        testSubscription = new Subscription();
        testSubscription.setName("ExBird Webhook Tier");
        testSubscription.setPrice(new BigDecimal("50000"));
        testSubscription.setDurationDays(30);
        testSubscription = subscriptionRepository.save(testSubscription);

        // 3. Simulate PayOS Link creation by injecting the context into Redis manually
        String cacheKey = "pending_payment:" + testOrderCode;
        PendingPaymentContext context = new PendingPaymentContext(testUser.getId(), testSubscription.getId());
        redisTemplate.opsForValue().set(cacheKey, context, Duration.ofHours(24));
    }

    @Test
    void shouldProcessValidWebhookActivateSubscriptionAndPublishEvent() throws Exception {
        // Arrange: Generate a perfectly valid HMAC signature exactly how PayOS does it
        String description = "Birdlens ExBird Webhook Tier";
        if (description.length() > 25) description = description.substring(0, 25);

        String dataStr = String.format("amount=%d&cancelUrl=%s&description=%s&orderCode=%d&returnUrl=%s",
                50000, cancelUrl, description, testOrderCode, returnUrl);

        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(checksumKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);

        byte[] hashBytes = sha256_HMAC.doFinal(dataStr.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        String validSignature = hexString.toString();

        PayOSWebhookPayload.Data payloadData = new PayOSWebhookPayload.Data(
                testOrderCode, 50000, description, "123", "ref", "time", "VND", "link123", "00", "success", "bid", "bname", "bnum", "bbank"
        );
        PayOSWebhookPayload payload = new PayOSWebhookPayload("00", "success", true, payloadData, validSignature);

        // Act
        client.post().uri("/api/v1/webhooks/payos")
                // Deliberately DO NOT pass Authorization JWT header since this is public
                .bodyValue(payload)
                .exchange()
                .expectStatus().isOk();

        // Assert 1: Database Updated
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updatedUser.getSubscription()).isNotNull();
        assertThat(updatedUser.getSubscription().getId()).isEqualTo(testSubscription.getId());
        assertThat(updatedUser.getSubscriptionExpiresAt()).isNotNull();

        // Assert 2: Redis Context Cleared
        String cacheKey = "pending_payment:" + testOrderCode;
        Object cachedContext = redisTemplate.opsForValue().get(cacheKey);
        assertThat(cachedContext).isNull();

        // Assert 3: RabbitMQ Notification Fired
        SubscriptionActivatedEvent event = (SubscriptionActivatedEvent) rabbitTemplate.receiveAndConvert(
                RabbitMQConfig.NOTIFICATIONS_QUEUE, 2000);

        assertThat(event).isNotNull();
        assertThat(event.userId()).isEqualTo(testUser.getId());
    }

    @Test
    void shouldRejectInvalidSignatureWith400() {
        PayOSWebhookPayload.Data payloadData = new PayOSWebhookPayload.Data(
                testOrderCode, 50000, "Fake Desc", "123", "ref", "time", "VND", "link123", "00", "success", "bid", "bname", "bnum", "bbank"
        );
        // Using a clearly fake signature
        PayOSWebhookPayload payload = new PayOSWebhookPayload("00", "success", true, payloadData, "invalid_fake_signature_123");

        client.post().uri("/api/v1/webhooks/payos")
                .bodyValue(payload)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("INVALID_SIGNATURE");
    }
}