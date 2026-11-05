package com.janseva.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProcessingOutboxRepository extends JpaRepository<ProcessingOutbox, Long> {

    Optional<ProcessingOutbox> findByAggregateTypeAndAggregateIdAndEventType(
            String aggregateType, Long aggregateId, String eventType);

    List<ProcessingOutbox> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
