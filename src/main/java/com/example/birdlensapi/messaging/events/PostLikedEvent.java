package com.example.birdlensapi.messaging.events;

import java.util.UUID;

public record PostLikedEvent(
        UUID postId,
        UUID likerUserId,
        UUID postOwnerUserId
) {}