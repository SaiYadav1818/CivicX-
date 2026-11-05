package com.janseva.embedding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "image_embeddings")
public class ImageEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_id", nullable = false)
    private Long mediaId;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private String vector;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ImageEmbedding() {
    }

    public ImageEmbedding(Long mediaId, String modelName, String modelVersion, String vector) {
        this.mediaId = mediaId;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.vector = vector;
    }

    public Long getId() {
        return id;
    }

    public Long getMediaId() {
        return mediaId;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getVector() {
        return vector;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
