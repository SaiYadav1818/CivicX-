package com.janseva.ai;

import com.janseva.complaint.Complaint;
import com.janseva.complaint.ComplaintCategory;
import com.janseva.complaint.ComplaintCategoryRepository;
import com.janseva.complaint.ComplaintRepository;
import com.janseva.config.CivicxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ClassificationService {

    private static final Logger log = LoggerFactory.getLogger(ClassificationService.class);
    private static final double HUMAN_REVIEW_CONFIDENCE = 0.40;

    private final ComplaintRepository complaintRepository;
    private final ComplaintCategoryRepository categoryRepository;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final List<ComplaintClassificationProvider> providers;
    private final CivicxProperties properties;

    public ClassificationService(ComplaintRepository complaintRepository,
                                 ComplaintCategoryRepository categoryRepository,
                                 AiAnalysisResultRepository aiAnalysisResultRepository,
                                 List<ComplaintClassificationProvider> providers,
                                 CivicxProperties properties) {
        this.complaintRepository = complaintRepository;
        this.categoryRepository = categoryRepository;
        this.aiAnalysisResultRepository = aiAnalysisResultRepository;
        this.providers = providers;
        this.properties = properties;
    }

    public boolean hasResult(Long complaintId) {
        return providers.stream()
                .anyMatch(p -> aiAnalysisResultRepository.existsByComplaintIdAndModelName(complaintId, p.modelName()));
    }

    @Transactional
    public void classifyComplaint(Long complaintId) {
        if (!properties.ai().enabled()) {
            log.info("AI classification disabled, skipping complaint {}", complaintId);
            return;
        }
        Complaint complaint = complaintRepository.findById(complaintId).orElse(null);
        if (complaint == null) {
            return;
        }
        for (ComplaintClassificationProvider provider : providers) {
            if (aiAnalysisResultRepository.existsByComplaintIdAndModelName(complaintId, provider.modelName())) {
                continue;
            }
            List<String> categoryCodes = categoryRepository.findAll().stream()
                    .map(ComplaintCategory::getCode)
                    .toList();
            ComplaintClassificationProvider.ClassificationResult result =
                    provider.classify(complaint.getTitle(), complaint.getDescription(), categoryCodes);

            aiAnalysisResultRepository.save(new AiAnalysisResult(
                    complaintId,
                    null,
                    provider.modelName(),
                    provider.modelVersion(),
                    true,
                    result.categoryCode(),
                    BigDecimal.valueOf(result.confidence()).setScale(4, RoundingMode.HALF_UP),
                    result.explanation(),
                    result.confidence() < HUMAN_REVIEW_CONFIDENCE
            ));
            log.info("Classified complaint {} as {} (confidence {}, method {})",
                    complaintId, result.categoryCode(), result.confidence(), provider.method());
        }
    }
}
