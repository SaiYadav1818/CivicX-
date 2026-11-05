package com.janseva.complaint;

import com.janseva.common.PagedResponse;
import com.janseva.complaint.dto.AiResultResponse;
import com.janseva.complaint.dto.ComplaintHistoryResponse;
import com.janseva.complaint.dto.ComplaintResponse;
import com.janseva.complaint.dto.ComplaintSummaryResponse;
import com.janseva.complaint.dto.RejectRequest;
import com.janseva.complaint.dto.StatusUpdateRequest;
import com.janseva.complaint.dto.VerifyRequest;
import com.janseva.duplicate.DuplicateDetectionResult;
import com.janseva.duplicate.DuplicateReviewService;
import com.janseva.duplicate.dto.DuplicateCandidateResponse;
import com.janseva.duplicate.dto.DuplicateReviewRequest;
import com.janseva.processing.ProcessingQueryService;
import com.janseva.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/complaints")
@Tag(name = "Complaints (Admin)", description = "Admin complaint review and status management. "
        + "Write operations require ADMIN; read operations allow ADMIN and SUPERVISOR.")
public class AdminComplaintController {

    private final ComplaintService complaintService;
    private final DuplicateReviewService duplicateReviewService;
    private final ProcessingQueryService processingQueryService;

    public AdminComplaintController(ComplaintService complaintService,
                                    DuplicateReviewService duplicateReviewService,
                                    ProcessingQueryService processingQueryService) {
        this.complaintService = complaintService;
        this.duplicateReviewService = duplicateReviewService;
        this.processingQueryService = processingQueryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    @Operation(summary = "List all complaints with filters",
            description = "Paged list with optional filters: status, categoryId, wardId, zoneId, created date range. "
                    + "Sort: one of id, createdAt, updatedAt, status, complaintNumber, e.g. 'createdAt,desc'.")
    public ResponseEntity<PagedResponse<ComplaintSummaryResponse>> list(
            @Parameter(description = "Filter by status") @RequestParam(required = false) ComplaintStatus status,
            @Parameter(description = "Filter by category id") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Filter by ward id") @RequestParam(required = false) Long wardId,
            @Parameter(description = "Filter by zone id") @RequestParam(required = false) Long zoneId,
            @Parameter(description = "Created from (ISO-8601)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Created until (ISO-8601)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @Parameter(description = "Sort expression", example = "createdAt,desc")
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return ResponseEntity.ok(PagedResponse.of(
                complaintService.listAdmin(status, categoryId, wardId, zoneId, from, to, page, size, sort)));
    }

    @GetMapping("/{complaintId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    @Operation(summary = "Get complaint details for review")
    public ResponseEntity<ComplaintResponse> get(@PathVariable Long complaintId) {
        return ResponseEntity.ok(complaintService.getForAdmin(complaintId));
    }

    @GetMapping("/{complaintId}/history")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    @Operation(summary = "Get complaint status history")
    public ResponseEntity<List<ComplaintHistoryResponse>> history(@PathVariable Long complaintId) {
        return ResponseEntity.ok(complaintService.getHistoryForAdmin(complaintId));
    }

    @PostMapping("/{complaintId}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Verify a complaint",
            description = "Moves the complaint to VERIFIED (allowed from SUBMITTED, UNDER_REVIEW or REOPENED).")
    public ResponseEntity<ComplaintResponse> verify(@PathVariable Long complaintId,
                                                    @RequestBody(required = false) @Valid VerifyRequest request) {
        String note = request != null ? request.note() : null;
        return ResponseEntity.ok(complaintService.verify(complaintId, CurrentUser.id(), note));
    }

    @PostMapping("/{complaintId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reject a complaint",
            description = "Moves the complaint to REJECTED (allowed from SUBMITTED or UNDER_REVIEW). "
                    + "A reason is mandatory.")
    public ResponseEntity<ComplaintResponse> reject(@PathVariable Long complaintId,
                                                    @Valid @RequestBody RejectRequest request) {
        return ResponseEntity.ok(complaintService.reject(complaintId, CurrentUser.id(), request));
    }

    @PatchMapping("/{complaintId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update complaint status (workflow-validated)",
            description = "Only transitions allowed by the status workflow are accepted; others return 409. "
                    + "REJECTED requires a reason. Status history is recorded on every change.")
    public ResponseEntity<ComplaintResponse> updateStatus(@PathVariable Long complaintId,
                                                          @Valid @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok(complaintService.updateStatus(complaintId, CurrentUser.id(), request));
    }

    @GetMapping("/{complaintId}/ai-results")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    @Operation(summary = "Get automated analysis results for a complaint",
            description = "Returns classifier results with model name, suggested category, confidence and "
                    + "explanation. The model name identifies the method (e.g. RULE_BASED keyword matching).")
    public ResponseEntity<List<AiResultResponse>> aiResults(@PathVariable Long complaintId) {
        return ResponseEntity.ok(processingQueryService.getAiResults(complaintId));
    }

    @GetMapping("/{complaintId}/duplicates")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    @Operation(summary = "Get duplicate candidates for a complaint",
            description = "Candidates computed from location distance, text similarity, image hash similarity and "
                    + "category match. The final decision is always made by a human reviewer.")
    public ResponseEntity<List<DuplicateCandidateResponse>> duplicates(@PathVariable Long complaintId) {
        return ResponseEntity.ok(processingQueryService.getDuplicates(complaintId));
    }

    @PostMapping("/{complaintId}/duplicates/{matchedComplaintId}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Confirm that a complaint is a duplicate",
            description = "Links the complaint to the earlier (primary) complaint. Neither complaint is deleted; "
                    + "both retain their history. Cycle-forming links are rejected with 409.")
    public ResponseEntity<DuplicateCandidateResponse> confirmDuplicate(
            @PathVariable Long complaintId,
            @PathVariable Long matchedComplaintId,
            @RequestBody(required = false) @Valid DuplicateReviewRequest request) {
        String note = request != null ? request.note() : null;
        DuplicateDetectionResult result = duplicateReviewService.confirm(
                complaintId, matchedComplaintId, CurrentUser.id(), note);
        return ResponseEntity.ok(processingQueryService.getDuplicates(complaintId).stream()
                .filter(d -> d.id().equals(result.getId()))
                .findFirst()
                .orElseThrow());
    }

    @PostMapping("/{complaintId}/duplicates/{matchedComplaintId}/dismiss")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Dismiss a duplicate candidate",
            description = "Marks the candidate as reviewed without linking the complaints.")
    public ResponseEntity<DuplicateCandidateResponse> dismissDuplicate(
            @PathVariable Long complaintId,
            @PathVariable Long matchedComplaintId,
            @RequestBody(required = false) @Valid DuplicateReviewRequest request) {
        String note = request != null ? request.note() : null;
        DuplicateDetectionResult result = duplicateReviewService.dismiss(
                complaintId, matchedComplaintId, CurrentUser.id(), note);
        return ResponseEntity.ok(processingQueryService.getDuplicates(complaintId).stream()
                .filter(d -> d.id().equals(result.getId()))
                .findFirst()
                .orElseThrow());
    }

    @PostMapping("/{complaintId}/ai-processing/retry")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Retry background processing for a complaint",
            description = "Re-queues the classification / hashing / duplicate-detection pipeline for a complaint "
                    + "whose processing failed.")
    public ResponseEntity<ProcessingQueryService.ProcessingRetryResponse> retryProcessing(@PathVariable Long complaintId) {
        return ResponseEntity.ok(processingQueryService.retry(complaintId));
    }
}
