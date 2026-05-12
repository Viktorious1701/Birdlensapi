package com.example.birdlensapi.domain.notification;

import com.example.birdlensapi.common.exception.ResourceNotFoundException;
import com.example.birdlensapi.domain.notification.dto.NotificationPageResponse;
import com.example.birdlensapi.domain.notification.dto.NotificationResponse;
import com.example.birdlensapi.domain.user.User;
import com.example.birdlensapi.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse getUserNotifications(UUID userId, Pageable pageable) {
        Page<UserNotification> notificationPage = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        List<NotificationResponse> content = notificationPage.getContent().stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());

        return new NotificationPageResponse(
                content,
                notificationPage.getNumber(),
                notificationPage.getSize(),
                notificationPage.getTotalElements(),
                notificationPage.getTotalPages(),
                notificationPage.isLast()
        );
    }

    @Transactional
    public NotificationResponse markAsRead(UUID notificationId, UUID requestingUserId) {
        UserNotification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        // Critical Security Check: Ensure the user trying to modify the notification actually owns it
        if (!notification.getUser().getId().equals(requestingUserId)) {
            throw new AccessDeniedException("You do not have permission to modify this notification");
        }

        notification.markAsRead();
        UserNotification savedNotification = notificationRepository.save(notification);

        return NotificationResponse.fromEntity(savedNotification);
    }

    // -- Worker Background Methods Below --

    @Transactional
    public void createPostLikedNotification(UUID receiverId, UUID actorId) {
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiver not found"));
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Actor not found"));

        String message = String.format("%s liked your post.", actor.getDisplayUsername());

        saveNotification(receiver, message, NotificationType.POST_LIKED);
    }

    @Transactional
    public void createNewCommentNotification(UUID receiverId, UUID actorId, String commentSnippet) {
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiver not found"));
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Actor not found"));

        String message = String.format("%s commented on your post: \"%s\"", actor.getDisplayUsername(), commentSnippet);

        saveNotification(receiver, message, NotificationType.NEW_COMMENT);
    }

    @Transactional
    public void createSubscriptionActivatedNotification(UUID receiverId) {
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiver not found"));

        String message = "Welcome to ExBird! Your premium subscription is now active.";

        saveNotification(receiver, message, NotificationType.SUBSCRIPTION_ACTIVATED);
    }

    private void saveNotification(User user, String message, NotificationType type) {
        UserNotification notification = new UserNotification();
        notification.setUser(user);
        notification.setMessage(message);
        notification.setType(type);
        notificationRepository.save(notification);

        log.info("Created {} notification for user: {}", type, user.getId());
    }
}