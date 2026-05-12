package com.example.birdlensapi.domain.payment.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PaymentLinkRequest(
        @NotNull(message = "Subscription ID is required")
        UUID subscriptionId
) {}