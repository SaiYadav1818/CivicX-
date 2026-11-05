package com.janseva.processing;

import com.janseva.ai.ClassificationService;
import com.janseva.duplicate.ComplaintDuplicateDetectionService;
import com.janseva.embedding.ImageEmbeddingService;
import com.janseva.hashing.ImageHashService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ComplaintProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ComplaintProcessingService.class);

    private final ClassificationService classificationService;
    private final ImageHashService imageHashService;
    private final ImageEmbeddingService imageEmbeddingService;
    private final ComplaintDuplicateDetectionService duplicateDetectionService;

    public ComplaintProcessingService(ClassificationService classificationService,
                                      ImageHashService imageHashService,
                                      ImageEmbeddingService imageEmbeddingService,
                                      ComplaintDuplicateDetectionService duplicateDetectionService) {
        this.classificationService = classificationService;
        this.imageHashService = imageHashService;
        this.imageEmbeddingService = imageEmbeddingService;
        this.duplicateDetectionService = duplicateDetectionService;
    }

    /**
     * Runs the whole background pipeline for one complaint. Idempotent: every step
     * checks whether its result already exists, so re-processing an event does not
     * create duplicate AI/hash/embedding/duplicate records.
     */
    public void process(Long complaintId) {
        if (classificationService.hasResult(complaintId)) {
            log.debug("Complaint {} already processed, skipping", complaintId);
            return;
        }
        classificationService.classifyComplaint(complaintId);
        imageHashService.hashAllMedia(complaintId);
        imageEmbeddingService.embedAllMedia(complaintId);
        duplicateDetectionService.runDetection(complaintId);
        log.info("Background processing completed for complaint {}", complaintId);
    }
}
