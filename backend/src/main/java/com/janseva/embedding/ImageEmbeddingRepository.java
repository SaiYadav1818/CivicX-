package com.janseva.embedding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ImageEmbeddingRepository extends JpaRepository<ImageEmbedding, Long> {

    boolean existsByMediaIdAndModelName(Long mediaId, String modelName);

    List<ImageEmbedding> findByMediaIdIn(Collection<Long> mediaIds);
}
