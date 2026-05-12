package com.example.birdlensapi.messaging.events;

import java.util.UUID;

public record SubscriptionActivatedEvent(UUID userId, UUID subscriptionId) {}