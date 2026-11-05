package com.janseva.duplicate;

import com.janseva.audit.AuditService;
import com.janseva.common.ApiException;
import com.janseva.complaint.Complaint;
import com.janseva.complaint.ComplaintRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class DuplicateReviewService {

    public static final String DUP_CONFIRMED = "DUPLICATE_CONFIRMED";
    public static final String DUP_DISMISSED = "DUPLICATE_DISMISSED";

    private final DuplicateDetectionResultRepository duplicateResultRepository;
    private final DuplicateComplaintLinkRepository duplicateLinkRepository;
    private final ComplaintRepository complaintRepository;
    private final AuditService auditService;

    public DuplicateReviewService(DuplicateDetectionResultRepository duplicateResultRepository,
                                  DuplicateComplaintLinkRepository duplicateLinkRepository,
                                  ComplaintRepository complaintRepository,
                                  AuditService auditService) {
        this.duplicateResultRepository = duplicateResultRepository;
        this.duplicateLinkRepository = duplicateLinkRepository;
        this.complaintRepository = complaintRepository;
        this.auditService = auditService;
    }

    @Transactional
    public DuplicateDetectionResult confirm(Long complaintId, Long matchedComplaintId, Long adminId, String note) {
        requireDistinct(complaintId, matchedComplaintId);
        Complaint complaint = requireComplaint(complaintId);
        Complaint primary = requireComplaint(matchedComplaintId);

        DuplicateDetectionResult result = duplicateResultRepository
                .findByComplaintIdAndMatchingComplaintId(complaintId, matchedComplaintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "No duplicate candidate links these complaints"));

        if (result.getStatus() != DuplicateReviewStatus.PENDING_ADMIN) {
            throw new ApiException(HttpStatus.CONFLICT, "This duplicate candidate has already been reviewed");
        }

        preventCycle(complaintId, matchedComplaintId);

        if (duplicateLinkRepository.existsByPrimaryComplaintIdAndDuplicateComplaintId(
                matchedComplaintId, complaintId)) {
            throw new ApiException(HttpStatus.CONFLICT, "These complaints are already linked");
        }

        duplicateLinkRepository.save(new DuplicateComplaintLink(
                matchedComplaintId, complaintId, adminId, note));

        result.setStatus(DuplicateReviewStatus.CONFIRMED);
        result.setReviewedBy(adminId);
        result.setReviewedAt(Instant.now());
        result.setReviewNote(note);
        duplicateResultRepository.save(result);

        auditService.log(adminId, DUP_CONFIRMED, "DUPLICATE",
                result.getId().toString(),
                Map.of("complaintNumber", complaint.getComplaintNumber(),
                        "primaryComplaintNumber", primary.getComplaintNumber()));
        return result;
    }

    @Transactional
    public DuplicateDetectionResult dismiss(Long complaintId, Long matchedComplaintId, Long adminId, String note) {
        DuplicateDetectionResult result = duplicateResultRepository
                .findByComplaintIdAndMatchingComplaintId(complaintId, matchedComplaintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "No duplicate candidate links these complaints"));

        if (result.getStatus() != DuplicateReviewStatus.PENDING_ADMIN) {
            throw new ApiException(HttpStatus.CONFLICT, "This duplicate candidate has already been reviewed");
        }

        result.setStatus(DuplicateReviewStatus.DISMISSED);
        result.setReviewedBy(adminId);
        result.setReviewedAt(Instant.now());
        result.setReviewNote(note);
        duplicateResultRepository.save(result);

        auditService.log(adminId, DUP_DISMISSED, "DUPLICATE",
                result.getId().toString(),
                Map.of("complaintId", complaintId, "matchedComplaintId", matchedComplaintId));
        return result;
    }

    /**
     * Linking complaint C as a duplicate of primary M is only safe when M is not
     * already (transitively) a duplicate of C. Walking M's "primary" chain and
     * rejecting the link if it reaches C prevents cycles.
     */
    private void preventCycle(Long complaintId, Long matchedComplaintId) {
        Set<Long> visited = new HashSet<>();
        Long current = matchedComplaintId;
        while (current != null && visited.add(current)) {
            if (current.equals(complaintId)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Confirming this duplicate would create a cycle");
            }
            Long next = null;
            for (DuplicateComplaintLink link : duplicateLinkRepository.findByDuplicateComplaintId(current)) {
                next = link.getPrimaryComplaintId();
                break;
            }
            current = next;
        }
    }

    private void requireDistinct(Long complaintId, Long matchedComplaintId) {
        if (complaintId.equals(matchedComplaintId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A complaint cannot be a duplicate of itself");
        }
    }

    private Complaint requireComplaint(Long complaintId) {
        return complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found"));
    }
}
