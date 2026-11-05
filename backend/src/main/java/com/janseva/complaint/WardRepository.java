package com.janseva.complaint;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WardRepository extends JpaRepository<Ward, Long> {

    Optional<Ward> findByIdAndActiveTrue(Long id);
}
