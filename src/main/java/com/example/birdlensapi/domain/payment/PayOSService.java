package com.example.birdlensapi.domain.payment;

import com.example.birdlensapi.common.exception.ResourceNotFoundException;
import com.example.birdlensapi.config.RabbitMQConfig;
import com.example.birdlensapi.domain.payment.dto.PayOSCreatePaymentRequest;
import com.example.birdlensapi.domain.payment.dto.PayOSCreatePaymentResponse;
import com.example.birdlensapi.domain.payment.dto.PayOSWebhookPayload;
import com.example.birdlensapi.domain.payment.dto.PaymentLinkResponse;
import com.example.birdlensapi.domain.subscription.Subscription;
import com.example.birdlensapi.domain.subscription.SubscriptionRepository;
import com.example.birdlensapi.domain.user.User;
import com.example.birdlensapi.domain.user.UserRepository;
import com.example.birdlensapi.messaging.events.SubscriptionActivatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class PayOSService {

    private static final Logger log = LoggerFactory.getLogger(PayOSService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final WebClient webClient;

    @Value("${app.payos.client-id}")
    private String clientId;

    @Value("${app.payos.api-key}")
    private String apiKey;

    @Value("${app.payos.checksum-key}")
    private String checksumKey;

    @Value("${app.payos.return-url}")
    private String returnUrl;

    @Value("${app.payos.cancel-url}")
    private String cancelUrl;

    public PayOSService(SubscriptionRepository subscriptionRepository,
                        UserRepository userRepository,
                        RedisTemplate<String, Object> redisTemplate,
                        RabbitTemplate rabbitTemplate,
                        @Value("${app.payos.base-url}") String baseUrl) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public PaymentLinkResponse initiateSubscriptionPayment(UUID subscriptionId, UUID userId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found"));

        long orderCode = System.currentTimeMillis();
        int amount = subscription.getPrice().intValue();

        String description = "Birdlens " + subscription.getName();
        if (description.length() > 25) {
            description = description.substring(0, 25);
        }

        String signature = generateCreationSignature(amount, cancelUrl, description, orderCode, returnUrl);

        PayOSCreatePaymentRequest payOSRequest = new PayOSCreatePaymentRequest(
                orderCode,
                amount,
                description,
                returnUrl,
                cancelUrl,
                signature
        );

        PayOSCreatePaymentResponse response = webClient.post()
                .uri("/v2/payment-requests")
                .header("x-client-id", clientId)
                .header("x-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payOSRequest)
                .retrieve()
                .bodyToMono(PayOSCreatePaymentResponse.class)
                .block();

        if (response == null || !"00".equals(response.code()) || response.data() == null) {
            throw new RuntimeException("Failed to generate payment link from PayOS");
        }

        String cacheKey = "pending_payment:" + orderCode;
        PendingPaymentContext context = new PendingPaymentContext(userId, subscription.getId());
        redisTemplate.opsForValue().set(cacheKey, context, Duration.ofHours(24));

        return new PaymentLinkResponse(response.data().checkoutUrl(), orderCode);
    }

    @Transactional
    public void processWebhook(PayOSWebhookPayload payload) {
        // 1. Verify Signature
        if (!verifyWebhookSignature(payload.data(), payload.signature())) {
            log.error("Invalid PayOS Webhook signature detected. Potential spoofing attack. OrderCode: {}", payload.data().orderCode());
            throw new IllegalArgumentException("Invalid signature");
        }

        // PayOS successful payment code is "00"
        if (!"00".equals(payload.code())) {
            log.info("Received non-success webhook from PayOS: {} for OrderCode: {}", payload.code(), payload.data().orderCode());
            return;
        }

        String cacheKey = "pending_payment:" + payload.data().orderCode();
        PendingPaymentContext context = (PendingPaymentContext) redisTemplate.opsForValue().get(cacheKey);

        if (context == null) {
            log.warn("Received valid payment webhook but no pending context found in Redis for OrderCode: {}", payload.data().orderCode());
            return;
        }

        try {
            // 2. Fetch Entities
            User user = userRepository.findById(context.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found during subscription activation"));

            Subscription subscription = subscriptionRepository.findById(context.subscriptionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Subscription not found during activation"));

            // 3. Update User Subscription
            user.setSubscription(subscription);
            user.setSubscriptionExpiresAt(Instant.now().plus(Duration.ofDays(subscription.getDurationDays())));
            userRepository.save(user);

            // 4. Cleanup Redis
            redisTemplate.delete(cacheKey);

            // 5. Fire Notification Event
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATIONS_EXCHANGE,
                    RabbitMQConfig.NOTIFICATION_SUBSCRIPTION_ACTIVATED_ROUTING_KEY,
                    new SubscriptionActivatedEvent(user.getId(), subscription.getId())
            );

            log.info("Successfully activated subscription for User: {} via OrderCode: {}", user.getId(), payload.data().orderCode());
        } catch (Exception e) {
            log.error("Internal processing failed during webhook execution for OrderCode: {}", payload.data().orderCode(), e);
            // We do NOT throw the exception outwards because we must return 200 OK to PayOS to prevent retry storms.
        }
    }

    private boolean verifyWebhookSignature(PayOSWebhookPayload.Data data, String providedSignature) {
        if (data == null || providedSignature == null) {
            return false;
        }

        // PayOS Webhook validation requires signing specific fields sorted alphabetically
        String dataStr = String.format("amount=%d&cancelUrl=%s&description=%s&orderCode=%d&returnUrl=%s",
                data.amount(), cancelUrl, data.description(), data.orderCode(), returnUrl);

        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(checksumKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);

            byte[] hashBytes = sha256_HMAC.doFinal(dataStr.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().equals(providedSignature);
        } catch (Exception e) {
            log.error("Error calculating HMAC signature for verification", e);
            return false;
        }
    }

    private String generateCreationSignature(int amount, String cancelUrl, String description, long orderCode, String returnUrl) {
        String dataStr = String.format("amount=%d&cancelUrl=%s&description=%s&orderCode=%d&returnUrl=%s",
                amount, cancelUrl, description, orderCode, returnUrl);

        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(checksumKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);

            byte[] hashBytes = sha256_HMAC.doFinal(dataStr.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PayOS signature", e);
        }
    }
}