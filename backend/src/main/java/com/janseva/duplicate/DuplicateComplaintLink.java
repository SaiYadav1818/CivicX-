package com.janseva.duplicate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "duplicate_complaint_links")
public class DuplicateComplaintLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "primary_complaint_id", nullable = false)
    private Long primaryComplaintId;

    @Column(name = "duplicate_complaint_id", nullable = false)
    private Long duplicateComplaintId;

    @Column(name = "linked_by", nullable = false)
    private Long linkedBy;

    @Column(length = 1000)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected DuplicateComplaintLink() {
    }

    public DuplicateComplaintLink(Long primaryComplaintId, Long duplicateComplaintId, Long linkedBy, String reason) {
        this.primaryComplaintId = primaryComplaintId;
        this.duplicateComplaintId = duplicateComplaintId;
        this.linkedBy = linkedBy;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Long getPrimaryComplaintId() {
        return primaryComplaintId;
    }

    public Long getDuplicateComplaintId() {
        return duplicateComplaintId;
    }

    public Long getLinkedBy() {
        return linkedBy;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
