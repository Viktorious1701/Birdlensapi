package com.example.birdlensapi.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
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

    @Bean
    public DirectExchange postsExchange() {
        return new DirectExchange(POSTS_EXCHANGE);
    }

    @Bean
    public Queue imageProcessingQueue() {
        return new Queue(IMAGE_PROCESSING_QUEUE, true); // durable = true
    }

    @Bean
    public Binding bindingImageProcessingQueue(Queue imageProcessingQueue, DirectExchange postsExchange) {
        return BindingBuilder.bind(imageProcessingQueue).to(postsExchange).with(POST_CREATED_ROUTING_KEY);
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