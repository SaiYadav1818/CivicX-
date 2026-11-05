package com.janseva.outbox;

import com.janseva.processing.ComplaintProcessingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    public static final String EXCHANGE = "janseva.events.complaints";
    public static final String ROUTING_KEY = "complaint.created";

    private final ProcessingOutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final int maxAttempts;

    public OutboxRelay(ProcessingOutboxRepository outboxRepository,
                       RabbitTemplate rabbitTemplate,
                       com.janseva.config.CivicxProperties properties) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.maxAttempts = properties.outbox().maxAttempts();
    }

    @Scheduled(fixedDelayString = "${civicx.outbox.poll-ms:5000}")
    @Transactional
    public void relayPendingEvents() {
        List<ProcessingOutbox> pending = outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        for (ProcessingOutbox row : pending) {
            try {
                rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, new ComplaintProcessingMessage(
                        row.getAggregateId(), row.getId()));
                row.setStatus(OutboxStatus.SENT);
            } catch (Exception e) {
                row.setAttempts(row.getAttempts() + 1);
                row.setLastError(truncate(e.getMessage()));
                if (row.getAttempts() >= maxAttempts) {
                    row.setStatus(OutboxStatus.FAILED);
                    log.error("Outbox event {} gave up after {} attempts", row.getId(), row.getAttempts());
                } else {
                    log.warn("Outbox publish failed for event {} (attempt {}): {}",
                            row.getId(), row.getAttempts(), e.getMessage());
                }
            }
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    public static Instant now() {
        return Instant.now();
    }
}
