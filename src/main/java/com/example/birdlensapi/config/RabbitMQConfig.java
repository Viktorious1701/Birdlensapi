package com.example.birdlensapi.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String POSTS_EXCHANGE = "posts.exchange";
    public static final String IMAGE_PROCESSING_QUEUE = "image-processing-queue";
    public static final String POST_CREATED_ROUTING_KEY = "post.created";

    // Notifications Topology
    public static final String NOTIFICATIONS_EXCHANGE = "notifications.exchange";
    public static final String NOTIFICATIONS_QUEUE = "notifications-queue";

    // Explicit Routing Keys for the Topic Exchange
    public static final String NOTIFICATION_SUBSCRIPTION_ACTIVATED_ROUTING_KEY = "notification.subscription.activated";
    public static final String NOTIFICATION_POST_LIKED_ROUTING_KEY = "notification.post.liked";
    public static final String NOTIFICATION_POST_COMMENTED_ROUTING_KEY = "notification.post.commented";

    // Dead-Letter Exchange (DLX) setup
    public static final String DLX_EXCHANGE = "dlx.exchange";
    public static final String IMAGE_PROCESSING_DLQ = "image-processing-dlq";
    public static final String NOTIFICATIONS_DLQ = "notifications-dlq";

    @Bean
    public DirectExchange postsExchange() {
        return new DirectExchange(POSTS_EXCHANGE);
    }

    // Using Topic Exchange as requested in architecture docs for notifications
    @Bean
    public TopicExchange notificationsExchange() {
        return new TopicExchange(NOTIFICATIONS_EXCHANGE);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue imageProcessingQueue() {
        return QueueBuilder.durable(IMAGE_PROCESSING_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", IMAGE_PROCESSING_DLQ)
                .build();
    }

    @Bean
    public Queue notificationsQueue() {
        return QueueBuilder.durable(NOTIFICATIONS_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", NOTIFICATIONS_DLQ)
                .build();
    }

    @Bean
    public Queue imageProcessingDlq() {
        return QueueBuilder.durable(IMAGE_PROCESSING_DLQ).build();
    }

    @Bean
    public Queue notificationsDlq() {
        return QueueBuilder.durable(NOTIFICATIONS_DLQ).build();
    }

    @Bean
    public Binding bindingImageProcessingQueue() {
        return BindingBuilder.bind(imageProcessingQueue()).to(postsExchange()).with(POST_CREATED_ROUTING_KEY);
    }

    // Bind any key starting with "notification." to the notifications queue
    @Bean
    public Binding bindingNotificationsQueue() {
        return BindingBuilder.bind(notificationsQueue()).to(notificationsExchange()).with("notification.#");
    }

    @Bean
    public Binding bindingImageProcessingDlq() {
        return BindingBuilder.bind(imageProcessingDlq()).to(dlxExchange()).with(IMAGE_PROCESSING_DLQ);
    }

    @Bean
    public Binding bindingNotificationsDlq() {
        return BindingBuilder.bind(notificationsDlq()).to(dlxExchange()).with(NOTIFICATIONS_DLQ);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }
}