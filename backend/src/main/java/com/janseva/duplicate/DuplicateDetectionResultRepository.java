package com.janseva.duplicate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DuplicateDetectionResultRepository extends JpaRepository<DuplicateDetectionResult, Long> {

    List<DuplicateDetectionResult> findByComplaintIdOrderByCreatedAtAsc(Long complaintId);

    Optional<DuplicateDetectionResult> findByComplaintIdAndMatchingComplaintId(Long complaintId, Long matchingComplaintId);

    List<DuplicateDetectionResult> findByComplaintIdAndStatusInOrderByCreatedAtAsc(
            Long complaintId, Collection<DuplicateReviewStatus> statuses);

    long countByComplaintIdAndStatusIn(Long complaintId, Collection<DuplicateReviewStatus> statuses);

    @Modifying
    @Query("delete from DuplicateDetectionResult r where r.complaintId = :complaintId and r.status = :status")
    void deleteByComplaintIdAndStatus(@Param("complaintId") Long complaintId,
                                      @Param("status") DuplicateReviewStatus status);
}
