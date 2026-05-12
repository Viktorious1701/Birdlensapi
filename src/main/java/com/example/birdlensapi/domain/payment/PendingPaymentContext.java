package com.example.birdlensapi.domain.payment;

import java.util.UUID;

public record PendingPaymentContext(
        UUID userId,
        UUID subscriptionId
) {}