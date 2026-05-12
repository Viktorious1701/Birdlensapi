package com.example.birdlensapi.domain.payment.dto;

public record PaymentLinkResponse(
        String checkoutUrl,
        long orderCode
) {}