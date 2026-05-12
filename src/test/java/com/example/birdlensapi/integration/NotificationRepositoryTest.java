package com.example.birdlensapi.integration;

import com.example.birdlensapi.domain.notification.NotificationRepository;
import com.example.birdlensapi.domain.notification.NotificationType;
import com.example.birdlensapi.domain.notification.UserNotification;
import com.example.birdlensapi.domain.user.User;
import com.example.birdlensapi.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldSaveAndRetrieveNotificationWithDefaults() {
        // Arrange: Create a user
        User user = new User("notification@example.com", "notifuser", "securepass");
        user = userRepository.save(user);

        // Act: Create and save a notification
        UserNotification notification = new UserNotification();
        notification.setUser(user);
        notification.setMessage("Welcome to ExBird Premium!");
        notification.setType(NotificationType.SUBSCRIPTION_ACTIVATED);

        UserNotification savedNotification = notificationRepository.save(notification);

        // Assert: Verify defaults and population
        assertThat(savedNotification.getId()).isNotNull();
        assertThat(savedNotification.isRead()).isFalse(); // Should default to false
        assertThat(savedNotification.getCreatedAt()).isNotNull(); // Should be auto-populated by @CreationTimestamp

        // Act: Use the convenience method
        savedNotification.markAsRead();
        UserNotification updatedNotification = notificationRepository.save(savedNotification);

        // Assert: Value was changed
        assertThat(updatedNotification.isRead()).isTrue();
    }
}