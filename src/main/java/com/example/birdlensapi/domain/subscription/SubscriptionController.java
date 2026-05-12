package com.example.birdlensapi.domain.subscription;

import com.example.birdlensapi.common.dto.ApiResponse;
import com.example.birdlensapi.domain.subscription.dto.SubscriptionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SubscriptionResponse>>> getSubscriptions() {
        List<SubscriptionResponse> subscriptions = subscriptionService.getAvailableSubscriptions();
        return ResponseEntity.ok(ApiResponse.success(subscriptions));
    }
}