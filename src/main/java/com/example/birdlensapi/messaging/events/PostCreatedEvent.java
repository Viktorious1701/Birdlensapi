package com.example.birdlensapi.messaging.events;

import java.util.UUID;

public record PostCreatedEvent(UUID postId) {}