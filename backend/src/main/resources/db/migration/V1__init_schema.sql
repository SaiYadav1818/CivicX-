-- JanSeva initial schema
-- All timestamps stored in UTC. Coordinates as DECIMAL.

-- ============================================================
-- AUTHENTICATION AND USERS
-- ============================================================

CREATE TABLE users (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    phone           VARCHAR(20)     NULL,
    full_name       VARCHAR(150)    NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    is_email_verified BOOLEAN       NOT NULL DEFAULT FALSE,
    last_login_at   DATETIME(3)     NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_phone (phone),
    KEY idx_users_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE roles (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50)     NOT NULL,
    description VARCHAR(255)    NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_roles (
    user_id     BIGINT UNSIGNED NOT NULL,
    role_id     BIGINT UNSIGNED NOT NULL,
    assigned_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    token_hash  VARCHAR(64)     NOT NULL,
    expires_at  DATETIME(3)     NOT NULL,
    revoked     BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_hash (token_hash),
    KEY idx_refresh_tokens_user (user_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_profiles (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    address     VARCHAR(500)    NULL,
    city        VARCHAR(100)    NULL,
    state       VARCHAR(100)    NULL,
    postal_code VARCHAR(20)     NULL,
    preferred_language VARCHAR(10) NOT NULL DEFAULT 'en',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_profiles_user (user_id),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- CIVIC COMPLAINT MANAGEMENT
-- ============================================================

CREATE TABLE complaint_categories (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code                VARCHAR(50)     NOT NULL,
    parent_id           BIGINT UNSIGNED NULL,
    name                VARCHAR(150)    NOT NULL,
    description         VARCHAR(500)    NULL,
    duplicate_radius_m  INT             NOT NULL DEFAULT 250,
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_complaint_categories_code (code),
    KEY idx_complaint_categories_parent (parent_id),
    CONSTRAINT fk_complaint_categories_parent FOREIGN KEY (parent_id) REFERENCES complaint_categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- GEOGRAPHY AND AUTHORITIES (reference tables used by complaints)
-- ============================================================

CREATE TABLE zones (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(150)    NOT NULL,
    code        VARCHAR(50)     NOT NULL,
    city        VARCHAR(100)    NOT NULL,
    state       VARCHAR(100)    NOT NULL,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_zones_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE wards (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    zone_id     BIGINT UNSIGNED NOT NULL,
    name        VARCHAR(150)    NOT NULL,
    code        VARCHAR(50)     NOT NULL,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wards_code (code),
    KEY idx_wards_zone (zone_id),
    CONSTRAINT fk_wards_zone FOREIGN KEY (zone_id) REFERENCES zones (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE departments (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name            VARCHAR(150)    NOT NULL,
    code            VARCHAR(50)     NOT NULL,
    description     VARCHAR(500)    NULL,
    default_category_id BIGINT UNSIGNED NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_departments_code (code),
    CONSTRAINT fk_departments_category FOREIGN KEY (default_category_id) REFERENCES complaint_categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE complaints (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    complaint_number    VARCHAR(30)     NOT NULL,
    citizen_id          BIGINT UNSIGNED NOT NULL,
    category_id         BIGINT UNSIGNED NOT NULL,
    status              VARCHAR(30)     NOT NULL DEFAULT 'SUBMITTED',
    title               VARCHAR(200)    NOT NULL,
    description         TEXT            NOT NULL,
    priority            VARCHAR(20)     NULL,
    ward_id             BIGINT UNSIGNED NULL,
    primary_location_id BIGINT UNSIGNED NULL,
    department_id       BIGINT UNSIGNED NULL,
    confirmed_duplicate_of BIGINT UNSIGNED NULL,
    verified_at         DATETIME(3)     NULL,
    resolved_at         DATETIME(3)     NULL,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_complaints_number (complaint_number),
    KEY idx_complaints_citizen (citizen_id),
    KEY idx_complaints_status (status),
    KEY idx_complaints_category (category_id),
    KEY idx_complaints_created (created_at),
    CONSTRAINT fk_complaints_citizen FOREIGN KEY (citizen_id) REFERENCES users (id),
    CONSTRAINT fk_complaints_category FOREIGN KEY (category_id) REFERENCES complaint_categories (id),
    CONSTRAINT fk_complaints_ward FOREIGN KEY (ward_id) REFERENCES wards (id),
    CONSTRAINT fk_complaints_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_complaints_confirmed_duplicate FOREIGN KEY (confirmed_duplicate_of) REFERENCES complaints (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE complaint_locations (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    complaint_id    BIGINT UNSIGNED NOT NULL,
    latitude        DECIMAL(10, 7)  NOT NULL,
    longitude       DECIMAL(10, 7)  NOT NULL,
    accuracy_meters DECIMAL(8, 2)   NULL,
    address         VARCHAR(500)    NULL,
    street          VARCHAR(200)    NULL,
    area            VARCHAR(200)    NULL,
    city            VARCHAR(100)    NULL,
    state           VARCHAR(100)    NULL,
    postal_code     VARCHAR(20)     NULL,
    location_source VARCHAR(30)     NOT NULL DEFAULT 'DEVICE_GPS',
    captured_at     DATETIME(3)     NOT NULL,
    is_current      BOOLEAN         NOT NULL DEFAULT TRUE,
    corrected_by    BIGINT UNSIGNED NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_complaint_locations_complaint (complaint_id),
    KEY idx_complaint_locations_coords (latitude, longitude),
    CONSTRAINT fk_complaint_locations_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE,
    CONSTRAINT fk_complaint_locations_corrected_by FOREIGN KEY (corrected_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE complaints
    ADD CONSTRAINT fk_complaints_primary_location FOREIGN KEY (primary_location_id) REFERENCES complaint_locations (id);

CREATE TABLE complaint_media (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    complaint_id    BIGINT UNSIGNED NOT NULL,
    object_key      VARCHAR(500)    NOT NULL,
    thumbnail_key   VARCHAR(500)    NULL,
    content_type    VARCHAR(100)    NOT NULL,
    file_size_bytes BIGINT          NOT NULL,
    width           INT             NULL,
    height          INT             NULL,
    processing_status VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    uploaded_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_complaint_media_complaint (complaint_id),
    KEY idx_complaint_media_status (processing_status),
    CONSTRAINT fk_complaint_media_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE complaint_history (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    complaint_id      BIGINT UNSIGNED NOT NULL,
    previous_status   VARCHAR(30)     NULL,
    new_status        VARCHAR(30)     NOT NULL,
    changed_by        BIGINT UNSIGNED NULL,
    reason            VARCHAR(1000)   NULL,
    evidence          JSON            NULL,
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_complaint_history_complaint (complaint_id, created_at),
    CONSTRAINT fk_complaint_history_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE,
    CONSTRAINT fk_complaint_history_changed_by FOREIGN KEY (changed_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE complaint_assignments (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    complaint_id    BIGINT UNSIGNED NOT NULL,
    department_id   BIGINT UNSIGNED NOT NULL,
    assigned_by     BIGINT UNSIGNED NOT NULL,
    assigned_to     BIGINT UNSIGNED NULL,
    notes           VARCHAR(1000)   NULL,
    assigned_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_complaint_assignments_complaint (complaint_id),
    KEY idx_complaint_assignments_department (department_id),
    CONSTRAINT fk_complaint_assignments_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE,
    CONSTRAINT fk_complaint_assignments_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_complaint_assignments_assigned_by FOREIGN KEY (assigned_by) REFERENCES users (id),
    CONSTRAINT fk_complaint_assignments_assigned_to FOREIGN KEY (assigned_to) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE complaint_feedback (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    complaint_id    BIGINT UNSIGNED NOT NULL,
    user_id         BIGINT UNSIGNED NOT NULL,
    rating          TINYINT         NULL,
    comment         VARCHAR(2000)   NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_complaint_feedback_complaint (complaint_id),
    CONSTRAINT fk_complaint_feedback_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE,
    CONSTRAINT fk_complaint_feedback_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- AI PROCESSING
-- ============================================================

CREATE TABLE ai_analysis_results (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    complaint_id          BIGINT UNSIGNED NOT NULL,
    media_id              BIGINT UNSIGNED NULL,
    model_name            VARCHAR(100)    NOT NULL,
    model_version         VARCHAR(50)     NOT NULL,
    detected              BOOLEAN         NOT NULL,
    category_code         VARCHAR(50)     NULL,
    subcategory_code      VARCHAR(50)     NULL,
    confidence            DECIMAL(5, 4)   NOT NULL,
    severity              VARCHAR(20)     NULL,
    description           VARCHAR(1000)   NULL,
    requires_human_review BOOLEAN         NOT NULL DEFAULT FALSE,
    quality_flags         JSON            NULL,
    is_original           BOOLEAN         NOT NULL DEFAULT TRUE,
    corrected_category_code VARCHAR(50)   NULL,
    corrected_by          BIGINT UNSIGNED NULL,
    processing_status     VARCHAR(20)     NOT NULL DEFAULT 'COMPLETED',
    created_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ai_analysis_complaint (complaint_id),
    KEY idx_ai_analysis_media (media_id),
    KEY idx_ai_analysis_category (category_code),
    CONSTRAINT fk_ai_analysis_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_analysis_media FOREIGN KEY (media_id) REFERENCES complaint_media (id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_analysis_corrected_by FOREIGN KEY (corrected_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_detected_objects (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ai_analysis_id      BIGINT UNSIGNED NOT NULL,
    object_label        VARCHAR(100)    NOT NULL,
    confidence          DECIMAL(5, 4)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ai_detected_objects_analysis (ai_analysis_id),
    CONSTRAINT fk_ai_detected_objects_analysis FOREIGN KEY (ai_analysis_id) REFERENCES ai_analysis_results (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE image_embeddings (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    media_id        BIGINT UNSIGNED NOT NULL,
    model_name      VARCHAR(100)    NOT NULL,
    model_version   VARCHAR(50)     NOT NULL,
    vector          JSON            NOT NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_image_embeddings_media (media_id),
    CONSTRAINT fk_image_embeddings_media FOREIGN KEY (media_id) REFERENCES complaint_media (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE image_hashes (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    media_id        BIGINT UNSIGNED NOT NULL,
    phash           CHAR(16)        NOT NULL,
    dhash           CHAR(16)        NOT NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_image_hashes_media (media_id),
    KEY idx_image_hashes_phash (phash),
    CONSTRAINT fk_image_hashes_media FOREIGN KEY (media_id) REFERENCES complaint_media (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE duplicate_detection_results (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    complaint_id            BIGINT UNSIGNED NOT NULL,
    duplicate_score         DECIMAL(5, 4)   NOT NULL,
    possible_duplicate      BOOLEAN         NOT NULL,
    recommended_action      VARCHAR(30)     NOT NULL,
    signals                 JSON            NOT NULL,
    matching_complaint_id   BIGINT UNSIGNED NULL,
    distance_meters         DECIMAL(10, 2)  NULL,
    image_similarity        DECIMAL(5, 4)   NULL,
    category_match          BOOLEAN         NULL,
    status                  VARCHAR(30)     NOT NULL DEFAULT 'PENDING_ADMIN',
    created_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_duplicate_results_complaint (complaint_id),
    KEY idx_duplicate_results_match (matching_complaint_id),
    KEY idx_duplicate_results_status (status),
    CONSTRAINT fk_duplicate_results_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE,
    CONSTRAINT fk_duplicate_results_match FOREIGN KEY (matching_complaint_id) REFERENCES complaints (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE duplicate_complaint_links (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    primary_complaint_id    BIGINT UNSIGNED NOT NULL,
    duplicate_complaint_id  BIGINT UNSIGNED NOT NULL,
    linked_by               BIGINT UNSIGNED NOT NULL,
    reason                  VARCHAR(1000)   NULL,
    created_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_duplicate_links_pair (primary_complaint_id, duplicate_complaint_id),
    KEY idx_duplicate_links_duplicate (duplicate_complaint_id),
    CONSTRAINT fk_duplicate_links_primary FOREIGN KEY (primary_complaint_id) REFERENCES complaints (id) ON DELETE CASCADE,
    CONSTRAINT fk_duplicate_links_duplicate FOREIGN KEY (duplicate_complaint_id) REFERENCES complaints (id) ON DELETE CASCADE,
    CONSTRAINT fk_duplicate_links_linked_by FOREIGN KEY (linked_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- GEOGRAPHY AND AUTHORITIES (remaining)
-- ============================================================

CREATE TABLE service_areas (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name            VARCHAR(150)    NOT NULL,
    boundary        GEOMETRY        NULL,
    city            VARCHAR(100)    NOT NULL,
    state           VARCHAR(100)    NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE authority_service_areas (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    department_id   BIGINT UNSIGNED NOT NULL,
    service_area_id BIGINT UNSIGNED NOT NULL,
    ward_id         BIGINT UNSIGNED NULL,
    zone_id         BIGINT UNSIGNED NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_authority_areas_department (department_id),
    CONSTRAINT fk_authority_areas_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE CASCADE,
    CONSTRAINT fk_authority_areas_service_area FOREIGN KEY (service_area_id) REFERENCES service_areas (id) ON DELETE CASCADE,
    CONSTRAINT fk_authority_areas_ward FOREIGN KEY (ward_id) REFERENCES wards (id),
    CONSTRAINT fk_authority_areas_zone FOREIGN KEY (zone_id) REFERENCES zones (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE authority_social_accounts (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    department_id   BIGINT UNSIGNED NOT NULL,
    platform        VARCHAR(30)     NOT NULL,
    handle          VARCHAR(150)    NOT NULL,
    url             VARCHAR(500)    NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_authority_social_department (department_id),
    CONSTRAINT fk_authority_social_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- ADMINISTRATION
-- ============================================================

CREATE TABLE audit_logs (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NULL,
    action          VARCHAR(50)     NOT NULL,
    entity_type     VARCHAR(50)     NOT NULL,
    entity_id       VARCHAR(50)     NULL,
    details         JSON            NULL,
    ip_address      VARCHAR(45)     NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_audit_logs_user (user_id),
    KEY idx_audit_logs_entity (entity_type, entity_id),
    KEY idx_audit_logs_created (created_at),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notifications (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    type            VARCHAR(50)     NOT NULL,
    title           VARCHAR(200)    NOT NULL,
    message         VARCHAR(2000)   NOT NULL,
    complaint_id    BIGINT UNSIGNED NULL,
    is_read         BOOLEAN         NOT NULL DEFAULT FALSE,
    dedup_key       VARCHAR(100)    NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notifications_dedup (dedup_key),
    KEY idx_notifications_user (user_id, is_read),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE escalation_rules (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name                    VARCHAR(150)    NOT NULL,
    category_id             BIGINT UNSIGNED NULL,
    severity                VARCHAR(20)     NULL,
    max_hours               INT             NOT NULL,
    target_department_id    BIGINT UNSIGNED NOT NULL,
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_escalation_rules_category FOREIGN KEY (category_id) REFERENCES complaint_categories (id),
    CONSTRAINT fk_escalation_rules_department FOREIGN KEY (target_department_id) REFERENCES departments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
