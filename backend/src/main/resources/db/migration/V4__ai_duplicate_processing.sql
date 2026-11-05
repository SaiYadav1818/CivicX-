-- Phase 3: AI-assisted classification, image hashing and duplicate detection
-- Additive changes only. Existing migrations untouched.

-- image_hashes: allow one row per media, add SHA-256 for exact duplicate detection.
-- phash/dhash become nullable because perceptual hashes cannot be computed for
-- formats the JVM cannot decode (e.g. WebP without a plugin).
ALTER TABLE image_hashes
    MODIFY phash VARCHAR(16) NULL,
    MODIFY dhash VARCHAR(16) NULL,
    ADD COLUMN sha256 VARCHAR(64) NULL AFTER media_id,
    ADD KEY idx_image_hashes_sha256 (sha256),
    ADD UNIQUE KEY uk_image_hashes_media (media_id);

-- duplicate_detection_results: record who reviewed a candidate and when.
ALTER TABLE duplicate_detection_results
    ADD COLUMN reviewed_by BIGINT UNSIGNED NULL AFTER status,
    ADD COLUMN reviewed_at DATETIME(3) NULL AFTER reviewed_by,
    ADD COLUMN review_note VARCHAR(1000) NULL AFTER reviewed_at,
    ADD KEY idx_duplicate_results_pair (complaint_id, matching_complaint_id),
    ADD CONSTRAINT fk_duplicate_results_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id);

-- Transactional outbox for reliable background processing:
-- the complaint transaction writes the event here; a relay publishes it to
-- RabbitMQ. Submission therefore never blocks on (or fails because of) the broker.
CREATE TABLE processing_outbox (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    aggregate_type  VARCHAR(50)     NOT NULL,
    aggregate_id    BIGINT UNSIGNED NOT NULL,
    event_type      VARCHAR(50)     NOT NULL,
    payload         JSON            NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    attempts        INT             NOT NULL DEFAULT 0,
    last_error      VARCHAR(1000)   NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    processed_at    DATETIME(3)     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event (aggregate_type, aggregate_id, event_type),
    KEY idx_outbox_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
