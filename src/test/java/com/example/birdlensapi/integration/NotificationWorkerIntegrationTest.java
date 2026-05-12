package com.example.birdlensapi.integration;

import com.example.birdlensapi.config.RabbitMQConfig;
import com.example.birdlensapi.domain.notification.NotificationRepository;
import com.example.birdlensapi.domain.notification.NotificationType;
import com.example.birdlensapi.domain.notification.UserNotification;
import com.example.birdlensapi.domain.user.User;
import com.example.birdlensapi.domain.user.UserRepository;
import com.example.birdlensapi.messaging.events.NewCommentEvent;
import com.example.birdlensapi.messaging.events.PostLikedEvent;
import com.example.birdlensapi.messaging.events.SubscriptionActivatedEvent;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"test", "worker"})
class NotificationWorkerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private User receiver;
    private User actor;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        userRepository.deleteAll();

        receiver = new User("receiver@example.com", "receiveruser", "pass");
        receiver = userRepository.save(receiver);

        actor = new User("actor@example.com", "actoruser", "pass");
        actor = userRepository.save(actor);
    }

    @Test
    void shouldProcessPostLikedEventAndCreateNotification() {
        UUID fakePostId = UUID.randomUUID();
        PostLikedEvent event = new PostLikedEvent(fakePostId, actor.getId(), receiver.getId());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.NOTIFICATIONS_EXCHANGE,
                RabbitMQConfig.NOTIFICATION_POST_LIKED_ROUTING_KEY,
                event
        );

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(notificationRepository.count()).isEqualTo(1);

                    UserNotification notification = notificationRepository.findAll().get(0);
                    assertThat(notification.getUser().getId()).isEqualTo(receiver.getId());
                    assertThat(notification.getType()).isEqualTo(NotificationType.POST_LIKED);
                    assertThat(notification.getMessage()).isEqualTo("actoruser liked your post.");
                    assertThat(notification.isRead()).isFalse();
                });
    }

    @Test
    void shouldProcessNewCommentEventAndCreateNotification() {
        UUID fakePostId = UUID.randomUUID();
        NewCommentEvent event = new NewCommentEvent(fakePostId, actor.getId(), receiver.getId(), "Great photo!");

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.NOTIFICATIONS_EXCHANGE,
                RabbitMQConfig.NOTIFICATION_POST_COMMENTED_ROUTING_KEY,
                event
        );

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(notificationRepository.count()).isEqualTo(1);

                    UserNotification notification = notificationRepository.findAll().get(0);
                    assertThat(notification.getType()).isEqualTo(NotificationType.NEW_COMMENT);
                    assertThat(notification.getMessage()).isEqualTo("actoruser commented on your post: \"Great photo!\"");
                });
    }

    @Test
    void shouldProcessSubscriptionActivatedEventAndCreateNotification() {
        UUID fakeSubscriptionId = UUID.randomUUID();
        SubscriptionActivatedEvent event = new SubscriptionActivatedEvent(receiver.getId(), fakeSubscriptionId);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.NOTIFICATIONS_EXCHANGE,
                RabbitMQConfig.NOTIFICATION_SUBSCRIPTION_ACTIVATED_ROUTING_KEY,
                event
        );

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(notificationRepository.count()).isEqualTo(1);

                    UserNotification notification = notificationRepository.findAll().get(0);
                    assertThat(notification.getType()).isEqualTo(NotificationType.SUBSCRIPTION_ACTIVATED);
                    assertThat(notification.getMessage()).contains("premium subscription is now active");
                });
    }
}