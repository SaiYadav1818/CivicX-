package com.janseva.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class OutboxEventPublisher {

    private final ProcessingOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(ProcessingOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishComplaintCreated(Long complaintId) {
        outboxRepository.findByAggregateTypeAndAggregateIdAndEventType(
                        ProcessingOutbox.AGGREGATE_COMPLAINT, complaintId,
                        ProcessingOutbox.EVENT_COMPLAINT_CREATED)
                .ifPresentOrElse(existing -> {
                    existing.setStatus(OutboxStatus.PENDING);
                    existing.setAttempts(0);
                    existing.setLastError(null);
                }, () -> {
                    String payload = toJson(Map.of("complaintId", complaintId));
                    outboxRepository.save(new ProcessingOutbox(
                            ProcessingOutbox.AGGREGATE_COMPLAINT, complaintId,
                            ProcessingOutbox.EVENT_COMPLAINT_CREATED, payload));
                });
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize outbox payload", e);
        }
    }
}
