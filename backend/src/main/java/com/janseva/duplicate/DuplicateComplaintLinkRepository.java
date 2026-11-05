package com.janseva.duplicate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DuplicateComplaintLinkRepository extends JpaRepository<DuplicateComplaintLink, Long> {

    List<DuplicateComplaintLink> findByDuplicateComplaintId(Long duplicateComplaintId);

    List<DuplicateComplaintLink> findByPrimaryComplaintId(Long primaryComplaintId);

    boolean existsByPrimaryComplaintIdAndDuplicateComplaintId(Long primaryComplaintId, Long duplicateComplaintId);
}
