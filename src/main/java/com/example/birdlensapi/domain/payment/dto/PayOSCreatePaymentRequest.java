package com.example.birdlensapi.domain.payment.dto;

public record PayOSCreatePaymentRequest(
        long orderCode,
        int amount,
        String description,
        String returnUrl,
        String cancelUrl,
        String signature
) {}