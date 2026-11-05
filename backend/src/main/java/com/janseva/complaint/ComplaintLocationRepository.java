package com.janseva.complaint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ComplaintLocationRepository extends JpaRepository<ComplaintLocation, Long>, JpaSpecificationExecutor<ComplaintLocation> {

    Optional<ComplaintLocation> findFirstByComplaintIdAndCurrentTrueOrderByIdDesc(Long complaintId);

    @Query("""
            select l from ComplaintLocation l
            where l.current = true
              and l.complaint.id <> :complaintId
              and l.latitude between :minLat and :maxLat
              and l.longitude between :minLng and :maxLng
            """)
    List<ComplaintLocation> findCurrentInBoundingBox(@Param("complaintId") Long complaintId,
                                                     @Param("minLat") BigDecimal minLat,
                                                     @Param("maxLat") BigDecimal maxLat,
                                                     @Param("minLng") BigDecimal minLng,
                                                     @Param("maxLng") BigDecimal maxLng);
}
