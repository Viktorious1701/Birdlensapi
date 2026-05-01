package com.example.birdlensapi.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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

    // Dead-Letter Exchange (DLX) setup
    public static final String DLX_EXCHANGE = "dlx.exchange";
    public static final String IMAGE_PROCESSING_DLQ = "image-processing-dlq";

    @Bean
    public DirectExchange postsExchange() {
        return new DirectExchange(POSTS_EXCHANGE);
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
    public Queue imageProcessingDlq() {
        return QueueBuilder.durable(IMAGE_PROCESSING_DLQ).build();
    }

    @Bean
    public Binding bindingImageProcessingQueue() {
        return BindingBuilder.bind(imageProcessingQueue()).to(postsExchange()).with(POST_CREATED_ROUTING_KEY);
    }

    @Bean
    public Binding bindingImageProcessingDlq() {
        return BindingBuilder.bind(imageProcessingDlq()).to(dlxExchange()).with(IMAGE_PROCESSING_DLQ);
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