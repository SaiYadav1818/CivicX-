package com.janseva.config;

import com.janseva.outbox.OutboxRelay;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String QUEUE_COMPLAINT_PROCESSING = "janseva.queue.complaint-processing";

    @Bean
    public DirectExchange complaintEventsExchange() {
        return new DirectExchange(OutboxRelay.EXCHANGE, true, false);
    }

    @Bean
    public Queue complaintProcessingQueue() {
        return new Queue(QUEUE_COMPLAINT_PROCESSING, true);
    }

    @Bean
    public Binding complaintProcessingBinding(DirectExchange complaintEventsExchange, Queue complaintProcessingQueue) {
        return BindingBuilder.bind(complaintProcessingQueue)
                .to(complaintEventsExchange)
                .with(OutboxRelay.ROUTING_KEY);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
