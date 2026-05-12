package com.example.birdlensapi.messaging.events;

import java.util.UUID;

public record NewCommentEvent(
        UUID postId,
        UUID commenterUserId,
        UUID postOwnerUserId,
        String commentSnippet
) {}