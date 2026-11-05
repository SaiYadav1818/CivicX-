package com.janseva.complaint;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ComplaintCategoryRepository extends JpaRepository<ComplaintCategory, Long> {

    Optional<ComplaintCategory> findByIdAndActiveTrue(Long id);

    Optional<ComplaintCategory> findByCode(String code);
}
