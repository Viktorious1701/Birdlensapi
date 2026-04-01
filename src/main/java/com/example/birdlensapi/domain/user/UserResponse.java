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
                user.getDisplayUsername(), // changed from getUsername()
                user.getFirstName(),
                user.getLastName(),
                user.getAvatarUrl(),
                user.getSubscriptionId(),
                user.getSubscriptionExpiresAt(),
                user.getCreatedAt()
        );
    }
}