package com.example.birdlensapi.domain.notification.dto;

import com.example.birdlensapi.domain.notification.NotificationType;
import com.example.birdlensapi.domain.notification.UserNotification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String message,
        NotificationType type,
        boolean isRead,
        Instant createdAt
) {
    public static NotificationResponse fromEntity(UserNotification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getMessage(),
                notification.getType(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}