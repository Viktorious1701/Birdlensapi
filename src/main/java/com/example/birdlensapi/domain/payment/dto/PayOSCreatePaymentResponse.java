package com.example.birdlensapi.domain.payment.dto;

public record PayOSCreatePaymentResponse(
        String code,
        String desc,
        Data data
) {
    public record Data(
            String checkoutUrl,
            String paymentLinkId
    ) {}
}