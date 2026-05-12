package com.example.birdlensapi.domain.payment;

import com.example.birdlensapi.common.exception.ResourceNotFoundException;
import com.example.birdlensapi.domain.payment.dto.PayOSCreatePaymentRequest;
import com.example.birdlensapi.domain.payment.dto.PayOSCreatePaymentResponse;
import com.example.birdlensapi.domain.payment.dto.PaymentLinkResponse;
import com.example.birdlensapi.domain.subscription.Subscription;
import com.example.birdlensapi.domain.subscription.SubscriptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Service
public class PayOSService {

    private final SubscriptionRepository subscriptionRepository;
    private final RedisTemplate<String, Object> redisTemplate;
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
                        RedisTemplate<String, Object> redisTemplate,
                        @Value("${app.payos.base-url}") String baseUrl) {
        this.subscriptionRepository = subscriptionRepository;
        this.redisTemplate = redisTemplate;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public PaymentLinkResponse initiateSubscriptionPayment(UUID subscriptionId, UUID userId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found"));

        // PayOS requires an integer/long orderCode, max 53 bit integer.
        // We use system time as a pseudo-random unique ID for MVP.
        long orderCode = System.currentTimeMillis();

        // Convert the BigDecimal price to integer (VND has no decimals practically)
        int amount = subscription.getPrice().intValue();

        // Description must be under 25 chars for PayOS
        String description = "Birdlens " + subscription.getName();
        if (description.length() > 25) {
            description = description.substring(0, 25);
        }

        // Generate the HMAC SHA256 Signature PayOS requires
        String signature = generateSignature(amount, cancelUrl, description, orderCode, returnUrl);

        PayOSCreatePaymentRequest payOSRequest = new PayOSCreatePaymentRequest(
                orderCode,
                amount,
                description,
                returnUrl,
                cancelUrl,
                signature
        );

        // Call External PayOS API
        PayOSCreatePaymentResponse response = webClient.post()
                .uri("/v2/payment-requests")
                .header("x-client-id", clientId)
                .header("x-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payOSRequest)
                .retrieve()
                .bodyToMono(PayOSCreatePaymentResponse.class)
                .block(); // Blocking is acceptable here as it's initiated by explicit user HTTP POST

        if (response == null || !"00".equals(response.code()) || response.data() == null) {
            throw new RuntimeException("Failed to generate payment link from PayOS");
        }

        // Store transaction state in Redis for 24 hours (PayOS links expire)
        String cacheKey = "pending_payment:" + orderCode;
        PendingPaymentContext context = new PendingPaymentContext(userId, subscription.getId());
        redisTemplate.opsForValue().set(cacheKey, context, Duration.ofHours(24));

        return new PaymentLinkResponse(response.data().checkoutUrl(), orderCode);
    }

    private String generateSignature(int amount, String cancelUrl, String description, long orderCode, String returnUrl) {
        // PayOS requires exact alphabetical order: amount=...&cancelUrl=...&description=...&orderCode=...&returnUrl=...
        String dataStr = String.format("amount=%d&cancelUrl=%s&description=%s&orderCode=%d&returnUrl=%s",
                amount, cancelUrl, description, orderCode, returnUrl);

        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(checksumKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);

            byte[] hashBytes = sha256_HMAC.doFinal(dataStr.getBytes(StandardCharsets.UTF_8));

            // Convert to HEX string
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