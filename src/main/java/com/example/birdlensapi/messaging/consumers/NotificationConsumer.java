package com.example.birdlensapi.messaging.consumers;

import com.example.birdlensapi.config.RabbitMQConfig;
import com.example.birdlensapi.domain.notification.NotificationService;
import com.example.birdlensapi.messaging.events.NewCommentEvent;
import com.example.birdlensapi.messaging.events.PostLikedEvent;
import com.example.birdlensapi.messaging.events.SubscriptionActivatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
@RabbitListener(queues = RabbitMQConfig.NOTIFICATIONS_QUEUE)
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationService notificationService;

    public NotificationConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitHandler
    public void handlePostLikedEvent(PostLikedEvent event) {
        log.info("Worker picked up PostLikedEvent for post: {}", event.postId());
        try {
            notificationService.createPostLikedNotification(event.postOwnerUserId(), event.likerUserId());
        } catch (Exception e) {
            log.error("Failed to process PostLikedEvent: {}", e.getMessage(), e);
            throw e; // Triggers Spring AMQP 3x retry -> DLQ fallback
        }
    }

    @RabbitHandler
    public void handleNewCommentEvent(NewCommentEvent event) {
        log.info("Worker picked up NewCommentEvent for post: {}", event.postId());
        try {
            notificationService.createNewCommentNotification(
                    event.postOwnerUserId(),
                    event.commenterUserId(),
                    event.commentSnippet()
            );
        } catch (Exception e) {
            log.error("Failed to process NewCommentEvent: {}", e.getMessage(), e);
            throw e;
        }
    }

    @RabbitHandler
    public void handleSubscriptionActivatedEvent(SubscriptionActivatedEvent event) {
        log.info("Worker picked up SubscriptionActivatedEvent for user: {}", event.userId());
        try {
            notificationService.createSubscriptionActivatedNotification(event.userId());
        } catch (Exception e) {
            log.error("Failed to process SubscriptionActivatedEvent: {}", e.getMessage(), e);
            throw e;
        }
    }
}