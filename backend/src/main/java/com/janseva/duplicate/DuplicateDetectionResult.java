package com.janseva.duplicate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "duplicate_detection_results")
public class DuplicateDetectionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;

    @Column(name = "duplicate_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal duplicateScore;

    @Column(name = "possible_duplicate", nullable = false)
    private boolean possibleDuplicate;

    @Column(name = "recommended_action", nullable = false, length = 30)
    private String recommendedAction;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private String signals;

    @Column(name = "matching_complaint_id")
    private Long matchingComplaintId;

    @Column(name = "distance_meters", precision = 10, scale = 2)
    private BigDecimal distanceMeters;

    @Column(name = "image_similarity", precision = 5, scale = 4)
    private BigDecimal imageSimilarity;

    @Column(name = "category_match")
    private Boolean categoryMatch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DuplicateReviewStatus status = DuplicateReviewStatus.PENDING_ADMIN;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected DuplicateDetectionResult() {
    }

    public DuplicateDetectionResult(Long complaintId, BigDecimal duplicateScore, boolean possibleDuplicate,
                                    String recommendedAction, String signals, Long matchingComplaintId,
                                    BigDecimal distanceMeters, BigDecimal imageSimilarity, Boolean categoryMatch) {
        this.complaintId = complaintId;
        this.duplicateScore = duplicateScore;
        this.possibleDuplicate = possibleDuplicate;
        this.recommendedAction = recommendedAction;
        this.signals = signals;
        this.matchingComplaintId = matchingComplaintId;
        this.distanceMeters = distanceMeters;
        this.imageSimilarity = imageSimilarity;
        this.categoryMatch = categoryMatch;
    }

    public Long getId() {
        return id;
    }

    public Long getComplaintId() {
        return complaintId;
    }

    public BigDecimal getDuplicateScore() {
        return duplicateScore;
    }

    public boolean isPossibleDuplicate() {
        return possibleDuplicate;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public Long getMatchingComplaintId() {
        return matchingComplaintId;
    }

    public BigDecimal getDistanceMeters() {
        return distanceMeters;
    }

    public BigDecimal getImageSimilarity() {
        return imageSimilarity;
    }

    public Boolean getCategoryMatch() {
        return categoryMatch;
    }

    public String getSignals() {
        return signals;
    }

    public DuplicateReviewStatus getStatus() {
        return status;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setStatus(DuplicateReviewStatus status) {
        this.status = status;
    }

    public void setReviewedBy(Long reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }
}
