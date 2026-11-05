package com.janseva.processing;

import com.janseva.config.RabbitConfig;
import com.janseva.outbox.OutboxStatus;
import com.janseva.outbox.ProcessingOutbox;
import com.janseva.outbox.ProcessingOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class ComplaintProcessingConsumer {

    private static final Logger log = LoggerFactory.getLogger(ComplaintProcessingConsumer.class);

    private final ComplaintProcessingService processingService;
    private final ProcessingOutboxRepository outboxRepository;

    public ComplaintProcessingConsumer(ComplaintProcessingService processingService,
                                       ProcessingOutboxRepository outboxRepository) {
        this.processingService = processingService;
        this.outboxRepository = outboxRepository;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE_COMPLAINT_PROCESSING)
    @Transactional
    public void onMessage(ComplaintProcessingMessage message) {
        Long complaintId = message.complaintId();
        try {
            processingService.process(complaintId);
            markProcessed(complaintId);
        } catch (Exception e) {
            log.error("Background processing failed for complaint {}", complaintId, e);
            markFailed(complaintId, e.getMessage());
        }
    }

    private void markProcessed(Long complaintId) {
        outboxRepository.findByAggregateTypeAndAggregateIdAndEventType(
                        ProcessingOutbox.AGGREGATE_COMPLAINT, complaintId,
                        ProcessingOutbox.EVENT_COMPLAINT_CREATED)
                .ifPresent(row -> {
                    row.setStatus(OutboxStatus.PROCESSED);
                    row.setProcessedAt(Instant.now());
                });
    }

    private void markFailed(Long complaintId, String error) {
        outboxRepository.findByAggregateTypeAndAggregateIdAndEventType(
                        ProcessingOutbox.AGGREGATE_COMPLAINT, complaintId,
                        ProcessingOutbox.EVENT_COMPLAINT_CREATED)
                .ifPresent(row -> {
                    row.setStatus(OutboxStatus.FAILED);
                    row.setAttempts(row.getAttempts() + 1);
                    row.setLastError(error != null && error.length() > 1000 ? error.substring(0, 1000) : error);
                });
    }
}
