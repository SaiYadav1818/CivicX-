package com.janseva.hashing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ImageHashRepository extends JpaRepository<ImageHash, Long> {

    boolean existsByMediaId(Long mediaId);

    List<ImageHash> findByMediaIdIn(Collection<Long> mediaIds);
}
