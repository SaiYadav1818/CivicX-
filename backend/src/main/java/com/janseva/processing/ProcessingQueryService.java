package com.janseva.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.janseva.ai.AiAnalysisResult;
import com.janseva.ai.AiAnalysisResultRepository;
import com.janseva.common.ApiException;
import com.janseva.complaint.Complaint;
import com.janseva.complaint.ComplaintCategoryRepository;
import com.janseva.complaint.ComplaintService;
import com.janseva.complaint.dto.AiResultResponse;
import com.janseva.complaint.dto.AiSummaryResponse;
import com.janseva.duplicate.DuplicateDetectionResult;
import com.janseva.duplicate.DuplicateDetectionResultRepository;
import com.janseva.duplicate.DuplicateReviewStatus;
import com.janseva.duplicate.dto.DuplicateCandidateResponse;
import com.janseva.outbox.OutboxStatus;
import com.janseva.outbox.ProcessingOutbox;
import com.janseva.outbox.ProcessingOutboxRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class ProcessingQueryService {

    private final ComplaintService complaintService;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final DuplicateDetectionResultRepository duplicateResultRepository;
    private final ComplaintCategoryRepository categoryRepository;
    private final ProcessingOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ProcessingQueryService(ComplaintService complaintService,
                                  AiAnalysisResultRepository aiAnalysisResultRepository,
                                  DuplicateDetectionResultRepository duplicateResultRepository,
                                  ComplaintCategoryRepository categoryRepository,
                                  ProcessingOutboxRepository outboxRepository,
                                  ObjectMapper objectMapper) {
        this.complaintService = complaintService;
        this.aiAnalysisResultRepository = aiAnalysisResultRepository;
        this.duplicateResultRepository = duplicateResultRepository;
        this.categoryRepository = categoryRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AiSummaryResponse getAiSummary(Long complaintId, Long userId) {
        complaintService.requireOwnerOrStaff(userId, complaintId);

        List<AiAnalysisResult> results = aiAnalysisResultRepository.findByComplaintIdOrderByCreatedAtAsc(complaintId);
        AiAnalysisResult latest = results.isEmpty() ? null : results.get(results.size() - 1);

        AiSummaryResponse.CategorySuggestion suggestion = null;
        if (latest != null) {
            String name = categoryRepository.findByCode(latest.getCategoryCode())
                    .map(c -> c.getName())
                    .orElse(latest.getCategoryCode());
            suggestion = new AiSummaryResponse.CategorySuggestion(
                    latest.getCategoryCode(),
                    name,
                    latest.getConfidence().doubleValue(),
                    methodOf(latest.getModelName())
            );
        }

        Set<DuplicateReviewStatus> active = Set.of(DuplicateReviewStatus.PENDING_ADMIN, DuplicateReviewStatus.CONFIRMED);
        long duplicateCount = duplicateResultRepository.countByComplaintIdAndStatusIn(complaintId, active);

        List<DuplicateDetectionResult> activeResults = duplicateResultRepository
                .findByComplaintIdAndStatusInOrderByCreatedAtAsc(complaintId, active);
        List<AiSummaryResponse.TopMatch> topMatches = activeResults.stream()
                .sorted(java.util.Comparator.comparing(DuplicateDetectionResult::getDuplicateScore).reversed())
                .limit(3)
                .map(r -> {
                    Complaint matched = r.getMatchingComplaintId() != null
                            ? complaintService.getRequired(r.getMatchingComplaintId()) : null;
                    return new AiSummaryResponse.TopMatch(
                            matched != null ? matched.getComplaintNumber() : null,
                            matched != null ? matched.getTitle() : null,
                            matched != null ? matched.getStatus().name() : null,
                            r.getDistanceMeters() != null ? r.getDistanceMeters().doubleValue() : null);
                })
                .toList();

        return new AiSummaryResponse(
                processingStatus(complaintId),
                suggestion,
                duplicateCount > 0,
                (int) duplicateCount,
                topMatches
        );
    }

    @Transactional(readOnly = true)
    public List<AiResultResponse> getAiResults(Long complaintId) {
        complaintService.getRequired(complaintId);
        return aiAnalysisResultRepository.findByComplaintIdOrderByCreatedAtAsc(complaintId).stream()
                .map(r -> new AiResultResponse(
                        r.getId(),
                        r.getModelName(),
                        r.getModelVersion(),
                        r.getCategoryCode(),
                        r.getConfidence().doubleValue(),
                        r.getDescription(),
                        r.isRequiresHumanReview(),
                        r.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DuplicateCandidateResponse> getDuplicates(Long complaintId) {
        complaintService.getRequired(complaintId);
        return duplicateResultRepository.findByComplaintIdOrderByCreatedAtAsc(complaintId).stream()
                .map(this::toCandidateResponse)
                .toList();
    }

    @Transactional
    public ProcessingRetryResponse retry(Long complaintId) {
        complaintService.getRequired(complaintId);
        ProcessingOutbox row = outboxRepository.findByAggregateTypeAndAggregateIdAndEventType(
                        ProcessingOutbox.AGGREGATE_COMPLAINT, complaintId,
                        ProcessingOutbox.EVENT_COMPLAINT_CREATED)
                .orElseGet(() -> {
                    String payload = "{\"complaintId\":" + complaintId + "}";
                    return outboxRepository.save(new ProcessingOutbox(
                            ProcessingOutbox.AGGREGATE_COMPLAINT, complaintId,
                            ProcessingOutbox.EVENT_COMPLAINT_CREATED, payload));
                });

        switch (row.getStatus()) {
            case FAILED -> {
                row.setStatus(OutboxStatus.PENDING);
                row.setAttempts(0);
                row.setLastError(null);
                return new ProcessingRetryResponse(row.getStatus().name(), "Processing re-queued");
            }
            case PROCESSED -> throw new ApiException(HttpStatus.CONFLICT, "Complaint already processed");
            default -> throw new ApiException(HttpStatus.CONFLICT, "Processing is already queued");
        }
    }

    private String processingStatus(Long complaintId) {
        return outboxRepository.findByAggregateTypeAndAggregateIdAndEventType(
                        ProcessingOutbox.AGGREGATE_COMPLAINT, complaintId,
                        ProcessingOutbox.EVENT_COMPLAINT_CREATED)
                .map(row -> switch (row.getStatus()) {
                    case PROCESSED -> "COMPLETED";
                    case FAILED -> "FAILED";
                    default -> "QUEUED";
                })
                .orElse("QUEUED");
    }

    private String methodOf(String modelName) {
        return modelName != null && modelName.contains("rule-based") ? "RULE_BASED" : modelName;
    }

    private DuplicateCandidateResponse toCandidateResponse(DuplicateDetectionResult result) {
        Complaint matched = result.getMatchingComplaintId() != null
                ? complaintService.getRequired(result.getMatchingComplaintId())
                : null;
        JsonNode signals = parseSignals(result.getSignals());
        return new DuplicateCandidateResponse(
                result.getId(),
                result.getMatchingComplaintId(),
                matched != null ? matched.getComplaintNumber() : null,
                matched != null ? matched.getTitle() : null,
                matched != null ? matched.getCategory().getName() : null,
                matched != null ? matched.getStatus().name() : null,
                result.getDistanceMeters() != null ? result.getDistanceMeters().doubleValue() : null,
                signals != null && signals.has("textSimilarity") && !signals.get("textSimilarity").isNull()
                        ? signals.get("textSimilarity").asDouble() : null,
                result.getImageSimilarity() != null ? result.getImageSimilarity().doubleValue() : null,
                result.getCategoryMatch(),
                result.getDuplicateScore().doubleValue(),
                result.isPossibleDuplicate(),
                result.getRecommendedAction(),
                result.getStatus().name(),
                result.getReviewedAt(),
                result.getReviewNote()
        );
    }

    private JsonNode parseSignals(String signals) {
        if (signals == null) {
            return null;
        }
        try {
            return objectMapper.readTree(signals);
        } catch (Exception e) {
            return null;
        }
    }

    public record ProcessingRetryResponse(String status, String message) {
    }
}
