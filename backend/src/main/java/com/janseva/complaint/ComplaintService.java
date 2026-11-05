package com.janseva.complaint;

import com.janseva.audit.AuditService;
import com.janseva.common.ApiException;
import com.janseva.complaint.dto.CategoryResponse;
import com.janseva.complaint.dto.ComplaintCreateRequest;
import com.janseva.complaint.dto.ComplaintHistoryResponse;
import com.janseva.complaint.dto.ComplaintMediaResponse;
import com.janseva.complaint.dto.ComplaintResponse;
import com.janseva.complaint.dto.ComplaintSummaryResponse;
import com.janseva.complaint.dto.LocationResponse;
import com.janseva.complaint.dto.RejectRequest;
import com.janseva.complaint.dto.StatusUpdateRequest;
import com.janseva.outbox.OutboxEventPublisher;
import com.janseva.security.CurrentUser;
import com.janseva.user.User;
import com.janseva.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ComplaintService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES =
            Set.of("id", "createdAt", "updatedAt", "status", "complaintNumber");

    private final ComplaintRepository complaintRepository;
    private final ComplaintLocationRepository complaintLocationRepository;
    private final ComplaintMediaRepository complaintMediaRepository;
    private final ComplaintHistoryRepository complaintHistoryRepository;
    private final ComplaintCategoryRepository categoryRepository;
    private final WardRepository wardRepository;
    private final ZoneRepository zoneRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final OutboxEventPublisher outboxEventPublisher;

    public ComplaintService(ComplaintRepository complaintRepository,
                            ComplaintLocationRepository complaintLocationRepository,
                            ComplaintMediaRepository complaintMediaRepository,
                            ComplaintHistoryRepository complaintHistoryRepository,
                            ComplaintCategoryRepository categoryRepository,
                            WardRepository wardRepository,
                            ZoneRepository zoneRepository,
                            UserRepository userRepository,
                            AuditService auditService,
                            OutboxEventPublisher outboxEventPublisher) {
        this.complaintRepository = complaintRepository;
        this.complaintLocationRepository = complaintLocationRepository;
        this.complaintMediaRepository = complaintMediaRepository;
        this.complaintHistoryRepository = complaintHistoryRepository;
        this.categoryRepository = categoryRepository;
        this.wardRepository = wardRepository;
        this.zoneRepository = zoneRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.outboxEventPublisher = outboxEventPublisher;
    }

    @Transactional
    public ComplaintResponse create(Long citizenId, ComplaintCreateRequest request) {
        User citizen = userRepository.findById(citizenId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required"));

        ComplaintCategory category = categoryRepository.findByIdAndActiveTrue(request.categoryId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Category not found or inactive"));

        Zone zone = null;
        Ward ward = null;
        if (request.zoneId() != null) {
            zone = zoneRepository.findByIdAndActiveTrue(request.zoneId())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Zone not found or inactive"));
        }
        if (request.wardId() != null) {
            ward = wardRepository.findByIdAndActiveTrue(request.wardId())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Ward not found or inactive"));
            if (zone != null && !ward.getZone().getId().equals(zone.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Ward does not belong to the given zone");
            }
        }

        Complaint complaint = new Complaint(citizen, category, ComplaintStatus.SUBMITTED,
                request.title().trim(), request.description().trim());
        complaint.setWard(ward);
        complaint.setZone(zone);
        complaintRepository.save(complaint);
        complaint.assignComplaintNumber("CX-" + String.format("%06d", complaint.getId()));

        ComplaintLocation location = new ComplaintLocation(
                complaint,
                request.latitude(),
                request.longitude(),
                request.accuracyMeters(),
                request.address(),
                request.landmark(),
                request.city(),
                request.state(),
                request.postalCode(),
                request.locationSource() != null ? request.locationSource() : LocationSource.DEVICE_GPS,
                Instant.now()
        );
        complaintLocationRepository.save(location);
        complaint.setPrimaryLocationId(location.getId());

        complaintHistoryRepository.save(new ComplaintHistory(
                complaint, null, ComplaintStatus.SUBMITTED, citizenId, "Complaint submitted"));

        auditService.log(citizenId, AuditService.COMPLAINT_CREATED, "COMPLAINT",
                complaint.getComplaintNumber(), Map.of("category", category.getCode()));

        outboxEventPublisher.publishComplaintCreated(complaint.getId());

        return toResponse(complaint);
    }

    @Transactional(readOnly = true)
    public Complaint requireOwnerOrStaff(Long userId, Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found"));
        requireOwnerOrStaff(userId, complaint);
        return complaint;
    }

    @Transactional(readOnly = true)
    public Page<ComplaintSummaryResponse> getMine(Long citizenId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return complaintRepository.findAll(citizenSpec(citizenId), pageRequest).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public ComplaintResponse getForOwner(Long citizenId, Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found"));
        requireOwnerOrStaff(citizenId, complaint);
        return toResponse(complaint);
    }

    @Transactional(readOnly = true)
    public ComplaintResponse getForAdmin(Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found"));
        return toResponse(complaint);
    }

    @Transactional(readOnly = true)
    public Page<ComplaintSummaryResponse> listAdmin(ComplaintStatus status, Long categoryId,
                                                    Long wardId, Long zoneId,
                                                    Instant from, Instant to,
                                                    int page, int size, String sort) {
        Specification<Complaint> spec = Specification.where(null);
        if (status != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        }
        if (categoryId != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("category").get("id"), categoryId));
        }
        if (wardId != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("ward").get("id"), wardId));
        }
        if (zoneId != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("zone").get("id"), zoneId));
        }
        if (from != null) {
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("createdAt"), to));
        }

        return complaintRepository.findAll(spec, toPageRequest(page, size, sort)).map(this::toSummary);
    }

    @Transactional
    public ComplaintResponse verify(Long complaintId, Long adminId, String note) {
        Complaint complaint = getRequired(complaintId);
        transition(complaint, ComplaintStatus.VERIFIED, adminId,
                note != null && !note.isBlank() ? note : "Complaint verified by admin");
        complaint.setVerifiedAt(Instant.now());
        auditService.log(adminId, AuditService.COMPLAINT_VERIFIED, "COMPLAINT",
                complaint.getComplaintNumber(), Map.of());
        return toResponse(complaint);
    }

    @Transactional
    public ComplaintResponse reject(Long complaintId, Long adminId, RejectRequest request) {
        Complaint complaint = getRequired(complaintId);
        transition(complaint, ComplaintStatus.REJECTED, adminId, request.reason().trim());
        auditService.log(adminId, AuditService.COMPLAINT_REJECTED, "COMPLAINT",
                complaint.getComplaintNumber(), Map.of("reason", request.reason().trim()));
        return toResponse(complaint);
    }

    @Transactional
    public ComplaintResponse updateStatus(Long complaintId, Long adminId, StatusUpdateRequest request) {
        Complaint complaint = getRequired(complaintId);
        ComplaintStatus target = request.status();

        if (target == ComplaintStatus.REJECTED && (request.reason() == null || request.reason().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reason is required to reject a complaint");
        }
        ComplaintStatus previous = complaint.getStatus();
        transition(complaint, target, adminId,
                request.reason() != null && !request.reason().isBlank() ? request.reason().trim() : null);

        if (target == ComplaintStatus.VERIFIED && complaint.getVerifiedAt() == null) {
            complaint.setVerifiedAt(Instant.now());
        }
        if (target == ComplaintStatus.RESOLVED && complaint.getResolvedAt() == null) {
            complaint.setResolvedAt(Instant.now());
        }
        auditService.log(adminId, AuditService.STATUS_CHANGED, "COMPLAINT",
                complaint.getComplaintNumber(), Map.of("from", previous.name(), "to", target.name()));
        return toResponse(complaint);
    }

    @Transactional(readOnly = true)
    public List<ComplaintHistoryResponse> getHistoryForOwner(Long citizenId, Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found"));
        requireOwnerOrStaff(citizenId, complaint);
        return complaintHistoryRepository.findByComplaintIdOrderByCreatedAtAsc(complaintId).stream()
                .map(h -> new ComplaintHistoryResponse(
                        h.getId(),
                        h.getPreviousStatus() != null ? h.getPreviousStatus().name() : null,
                        h.getNewStatus().name(),
                        h.getChangedBy(),
                        h.getReason(),
                        h.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ComplaintHistoryResponse> getHistoryForAdmin(Long complaintId) {
        if (!complaintRepository.existsById(complaintId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Complaint not found");
        }
        return complaintHistoryRepository.findByComplaintIdOrderByCreatedAtAsc(complaintId).stream()
                .map(h -> new ComplaintHistoryResponse(
                        h.getId(),
                        h.getPreviousStatus() != null ? h.getPreviousStatus().name() : null,
                        h.getNewStatus().name(),
                        h.getChangedBy(),
                        h.getReason(),
                        h.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Complaint getRequired(Long complaintId) {
        return complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found"));
    }

    private void transition(Complaint complaint, ComplaintStatus target, Long actorId, String reason) {
        ComplaintStatus current = complaint.getStatus();
        if (current == target) {
            return;
        }
        if (!ComplaintStatusWorkflow.canTransition(current, target)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Invalid status transition from " + current + " to " + target);
        }
        complaint.setStatus(target);
        complaintHistoryRepository.save(new ComplaintHistory(complaint, current, target, actorId, reason));
    }

    private void requireOwnerOrStaff(Long userId, Complaint complaint) {
        boolean isOwner = complaint.getCitizen().getId().equals(userId);
        boolean isStaff = CurrentUser.hasRole("ADMIN") || CurrentUser.hasRole("SUPERVISOR");
        if (!isOwner && !isStaff) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this complaint");
        }
    }

    private Specification<Complaint> citizenSpec(Long citizenId) {
        return (r, q, cb) -> cb.equal(r.get("citizen").get("id"), citizenId);
    }

    private PageRequest toPageRequest(int page, int size, String sort) {
        String property = "createdAt";
        Sort.Direction direction = Sort.Direction.DESC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            if (ALLOWED_SORT_PROPERTIES.contains(parts[0])) {
                property = parts[0];
            }
            if (parts.length > 1 && parts[1].equalsIgnoreCase("asc")) {
                direction = Sort.Direction.ASC;
            }
        }
        return PageRequest.of(page, size, Sort.by(direction, property));
    }

    private ComplaintSummaryResponse toSummary(Complaint complaint) {
        return new ComplaintSummaryResponse(
                complaint.getId(),
                complaint.getComplaintNumber(),
                complaint.getStatus().name(),
                complaint.getTitle(),
                complaint.getCategory().getName(),
                complaint.getCreatedAt(),
                complaint.getUpdatedAt()
        );
    }

    private ComplaintResponse toResponse(Complaint complaint) {
        ComplaintLocation location = complaint.getPrimaryLocationId() != null
                ? complaintLocationRepository.findById(complaint.getPrimaryLocationId()).orElse(null)
                : null;
        List<ComplaintMediaResponse> media = complaintMediaRepository
                .findByComplaintIdOrderByUploadedAtAsc(complaint.getId()).stream()
                .map(m -> new ComplaintMediaResponse(
                        m.getId(),
                        m.getOriginalFilename(),
                        m.getContentType(),
                        m.getFileSizeBytes(),
                        m.getWidth(),
                        m.getHeight(),
                        m.getProcessingStatus().name(),
                        m.getUploadedAt()))
                .toList();

        return new ComplaintResponse(
                complaint.getId(),
                complaint.getComplaintNumber(),
                complaint.getStatus().name(),
                complaint.getTitle(),
                complaint.getDescription(),
                new CategoryResponse(complaint.getCategory().getId(), complaint.getCategory().getCode(),
                        complaint.getCategory().getName()),
                location != null ? new LocationResponse(
                        location.getLatitude(),
                        location.getLongitude(),
                        location.getAccuracyMeters(),
                        location.getAddress(),
                        location.getLandmark(),
                        location.getCity(),
                        location.getState(),
                        location.getPostalCode(),
                        location.getLocationSource().name()) : null,
                complaint.getWard() != null ? complaint.getWard().getName() : null,
                complaint.getZone() != null ? complaint.getZone().getName() : null,
                media,
                complaint.getCreatedAt(),
                complaint.getUpdatedAt(),
                complaint.getVerifiedAt(),
                complaint.getResolvedAt()
        );
    }
}
