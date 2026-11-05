package com.janseva.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ai_analysis_results")
public class AiAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;

    @Column(name = "media_id")
    private Long mediaId;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @Column(nullable = false)
    private boolean detected;

    @Column(name = "category_code", length = 50)
    private String categoryCode;

    @Column(name = "subcategory_code", length = 50)
    private String subcategoryCode;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(length = 20)
    private String severity;

    @Column(length = 1000)
    private String description;

    @Column(name = "requires_human_review", nullable = false)
    private boolean requiresHumanReview = false;

    @Column(name = "is_original", nullable = false)
    private boolean original = true;

    @Column(name = "processing_status", nullable = false, length = 20)
    private String processingStatus = "COMPLETED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AiAnalysisResult() {
    }

    public AiAnalysisResult(Long complaintId, Long mediaId, String modelName, String modelVersion,
                            boolean detected, String categoryCode, BigDecimal confidence,
                            String description, boolean requiresHumanReview) {
        this.complaintId = complaintId;
        this.mediaId = mediaId;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.detected = detected;
        this.categoryCode = categoryCode;
        this.confidence = confidence;
        this.description = description;
        this.requiresHumanReview = requiresHumanReview;
    }

    public Long getId() {
        return id;
    }

    public Long getComplaintId() {
        return complaintId;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public boolean isDetected() {
        return detected;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequiresHumanReview() {
        return requiresHumanReview;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
