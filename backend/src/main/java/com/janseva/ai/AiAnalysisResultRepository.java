package com.janseva.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiAnalysisResultRepository extends JpaRepository<AiAnalysisResult, Long> {

    boolean existsByComplaintIdAndModelName(Long complaintId, String modelName);

    List<AiAnalysisResult> findByComplaintIdOrderByCreatedAtAsc(Long complaintId);
}
