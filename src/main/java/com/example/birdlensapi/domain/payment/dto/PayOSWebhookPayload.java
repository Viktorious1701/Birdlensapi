package com.example.birdlensapi.domain.payment.dto;

public record PayOSWebhookPayload(
        String code,
        String desc,
        boolean success,
        Data data,
        String signature
) {
    public record Data(
            long orderCode,
            int amount,
            String description,
            String accountNumber,
            String reference,
            String transactionDateTime,
            String currency,
            String paymentLinkId,
            String code,
            String desc,
            String counterAccountBankId,
            String counterAccountName,
            String counterAccountNumber,
            String counterAccountBankName
    ) {}
}