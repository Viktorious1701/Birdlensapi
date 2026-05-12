package com.example.birdlensapi.domain.user;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String username,
        String firstName,
        String lastName,
        String avatarUrl,
        UUID subscriptionId,
        Instant subscriptionExpiresAt,
        Instant createdAt
) {
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getAvatarUrl(),
                // Safely extract the ID from the Subscription object if the user has one
                user.getSubscription() != null ? user.getSubscription().getId() : null,
                user.getSubscriptionExpiresAt(),
                user.getCreatedAt()
        );
    }
}