package com.janseva.complaint;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintMediaRepository extends JpaRepository<ComplaintMedia, Long> {

    List<ComplaintMedia> findByComplaintIdOrderByUploadedAtAsc(Long complaintId);

    long countByComplaintId(Long complaintId);
}
