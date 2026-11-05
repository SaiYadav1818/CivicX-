package com.janseva.hashing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "image_hashes")
public class ImageHash {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_id", nullable = false, unique = true)
    private Long mediaId;

    @Column(length = 64)
    private String sha256;

    @Column(length = 16)
    private String phash;

    @Column(length = 16)
    private String dhash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ImageHash() {
    }

    public ImageHash(Long mediaId, String sha256, String phash, String dhash) {
        this.mediaId = mediaId;
        this.sha256 = sha256;
        this.phash = phash;
        this.dhash = dhash;
    }

    public Long getId() {
        return id;
    }

    public Long getMediaId() {
        return mediaId;
    }

    public String getSha256() {
        return sha256;
    }

    public String getPhash() {
        return phash;
    }

    public String getDhash() {
        return dhash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
