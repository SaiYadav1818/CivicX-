package com.janseva.complaint;

import com.janseva.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "complaints")
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_number", unique = true, length = 30)
    private String complaintNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citizen_id", nullable = false)
    private User citizen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ComplaintCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ComplaintStatus status = ComplaintStatus.SUBMITTED;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_id")
    private Ward ward;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Column(name = "primary_location_id")
    private Long primaryLocationId;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Complaint() {
    }

    public Complaint(User citizen, ComplaintCategory category, ComplaintStatus status, String title, String description) {
        this.citizen = citizen;
        this.category = category;
        this.status = status;
        this.title = title;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public String getComplaintNumber() {
        return complaintNumber;
    }

    public void assignComplaintNumber(String complaintNumber) {
        this.complaintNumber = complaintNumber;
    }

    public User getCitizen() {
        return citizen;
    }

    public ComplaintCategory getCategory() {
        return category;
    }

    public ComplaintStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Ward getWard() {
        return ward;
    }

    public Zone getZone() {
        return zone;
    }

    public Long getPrimaryLocationId() {
        return primaryLocationId;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setWard(Ward ward) {
        this.ward = ward;
    }

    public void setZone(Zone zone) {
        this.zone = zone;
    }

    public void setPrimaryLocationId(Long primaryLocationId) {
        this.primaryLocationId = primaryLocationId;
    }

    public void setStatus(ComplaintStatus status) {
        this.status = status;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
