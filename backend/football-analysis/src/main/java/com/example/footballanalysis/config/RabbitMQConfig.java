package com.example.footballanalysis.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "video-exchange";

    // --- OUTBOUND: SPRING TO PYTHON WORKERS ---
    public static final String ML_QUEUE_NAME = "video-processing-queue";
    public static final String ML_ROUTING_KEY = "video.process";

    public static final String ENCODER_QUEUE_NAME = "video-encoding-queue";
    public static final String ENCODER_ROUTING_KEY = "video.encode";

    public static final String CLIP_RENDER_QUEUE_NAME = "clip-render-queue";
    public static final String CLIP_RENDER_ROUTING_KEY = "clip.render";

    // --- INBOUND: PYTHON WORKERS TO SPRING ---
    public static final String ML_COMPLETED_QUEUE_NAME = "video-completed-queue";
    public static final String ML_COMPLETED_ROUTING_KEY = "video.completed";

    public static final String ENCODER_COMPLETED_QUEUE_NAME = "video-encoder-completed-queue";
    public static final String ENCODER_COMPLETED_ROUTING_KEY = "video.encoded";

    public static final String CLIP_RENDER_COMPLETED_QUEUE_NAME = "clip-render-completed-queue";
    public static final String CLIP_RENDER_COMPLETED_ROUTING_KEY = "clip.rendered";

    // 1. The Single Router (Direct Exchange)
    @Bean
    public DirectExchange videoExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    // ==========================================
    // OUTBOUND QUEUES (Tasks going to Python)
    // ==========================================

    @Bean
    public Queue mlProcessingQueue() {
        return new Queue(ML_QUEUE_NAME, true);
    }

    @Bean
    public Binding mlProcessingBinding() {
        return BindingBuilder.bind(mlProcessingQueue()).to(videoExchange()).with(ML_ROUTING_KEY);
    }

    @Bean
    public Queue encoderProcessingQueue() {
        return new Queue(ENCODER_QUEUE_NAME, true);
    }

    @Bean
    public Binding encoderProcessingBinding() {
        return BindingBuilder.bind(encoderProcessingQueue()).to(videoExchange()).with(ENCODER_ROUTING_KEY);
    }

    @Bean
    public Queue clipRenderQueue() {
        return new Queue(CLIP_RENDER_QUEUE_NAME, true);
    }

    @Bean
    public Binding clipRenderBinding() {
        return BindingBuilder.bind(clipRenderQueue()).to(videoExchange()).with(CLIP_RENDER_ROUTING_KEY);
    }

    // ==========================================
    // INBOUND QUEUES (Results coming to Spring)
    // ==========================================

    @Bean
    public Queue mlCompletedQueue() {
        return new Queue(ML_COMPLETED_QUEUE_NAME, true); // Existing ML worker uses this
    }

    @Bean
    public Binding mlCompletedBinding() {
        return BindingBuilder.bind(mlCompletedQueue()).to(videoExchange()).with(ML_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public Queue encoderCompletedQueue() {
        return new Queue(ENCODER_COMPLETED_QUEUE_NAME, true); // New Encoder worker uses this
    }

    @Bean
    public Binding encoderCompletedBinding() {
        return BindingBuilder.bind(encoderCompletedQueue()).to(videoExchange()).with(ENCODER_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public Queue clipRenderCompletedQueue() {
        return new Queue(CLIP_RENDER_COMPLETED_QUEUE_NAME, true);
    }

    @Bean
    public Binding clipRenderCompletedBinding() {
        return BindingBuilder.bind(clipRenderCompletedQueue()).to(videoExchange()).with(CLIP_RENDER_COMPLETED_ROUTING_KEY);
    }

    // ==========================================
    // JSON CONVERTER
    // ==========================================
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
