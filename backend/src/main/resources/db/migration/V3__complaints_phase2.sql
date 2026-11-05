-- Phase 2: citizen complaint management
-- - complaint_number becomes nullable so the application can assign the
--   concurrency-safe CX-000001 style reference from the row id after insert.
-- - zone_id for direct zone filtering; landmark on locations; sanitized
--   original filename on media.

ALTER TABLE complaints
    MODIFY complaint_number VARCHAR(30) NULL,
    ADD COLUMN zone_id BIGINT UNSIGNED NULL AFTER ward_id,
    ADD KEY idx_complaints_zone (zone_id),
    ADD KEY idx_complaints_status_created (status, created_at),
    ADD CONSTRAINT fk_complaints_zone FOREIGN KEY (zone_id) REFERENCES zones (id);

ALTER TABLE complaint_locations
    ADD COLUMN landmark VARCHAR(200) NULL AFTER address;

ALTER TABLE complaint_media
    ADD COLUMN original_filename VARCHAR(255) NULL AFTER object_key;
