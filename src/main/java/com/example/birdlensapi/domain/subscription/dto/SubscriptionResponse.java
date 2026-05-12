package com.example.birdlensapi.domain.subscription.dto;

import com.example.birdlensapi.domain.subscription.Subscription;

import java.math.BigDecimal;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        Integer durationDays,
        String productId
) {
    public static SubscriptionResponse fromEntity(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getName(),
                subscription.getDescription(),
                subscription.getPrice(),
                subscription.getDurationDays(),
                subscription.getProductId()
        );
    }
}