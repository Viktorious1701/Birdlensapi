package com.example.birdlensapi.domain.payment;

import com.example.birdlensapi.common.dto.ApiResponse;
import com.example.birdlensapi.domain.payment.dto.PayOSWebhookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/payos")
public class PayOSWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PayOSWebhookController.class);
    private final PayOSService payOSService;

    public PayOSWebhookController(PayOSService payOSService) {
        this.payOSService = payOSService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<String>> handleWebhook(@RequestBody PayOSWebhookPayload payload) {
        log.info("Received PayOS Webhook for OrderCode: {}",
                payload.data() != null ? payload.data().orderCode() : "UNKNOWN");

        try {
            payOSService.processWebhook(payload);
            return ResponseEntity.ok(ApiResponse.success("Webhook processed"));
        } catch (IllegalArgumentException e) {
            // Only return an error status (like 400) if the signature validation fails
            log.error("Invalid Webhook Signature rejected.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("INVALID_SIGNATURE", "The webhook signature is invalid"));
        }
        // If internal business logic failed (user not found, redis unavailable), we STILL return 200 OK
        // to stop PayOS from hammering the server with automated retries.
    }
}