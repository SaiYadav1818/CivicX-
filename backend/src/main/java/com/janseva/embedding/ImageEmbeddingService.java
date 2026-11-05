package com.janseva.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.janseva.complaint.ComplaintMedia;
import com.janseva.complaint.ComplaintMediaRepository;
import com.janseva.storage.ComplaintFileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
public class ImageEmbeddingService {

    public static final String MODEL_NAME = "civicx-local-embedding";
    public static final String MODEL_VERSION = "1.0";
    public static final int DIMENSIONS = 128;

    private static final Logger log = LoggerFactory.getLogger(ImageEmbeddingService.class);

    private final ComplaintMediaRepository complaintMediaRepository;
    private final ImageEmbeddingRepository imageEmbeddingRepository;
    private final ComplaintFileStorageService storageService;
    private final ObjectMapper objectMapper;

    public ImageEmbeddingService(ComplaintMediaRepository complaintMediaRepository,
                                 ImageEmbeddingRepository imageEmbeddingRepository,
                                 ComplaintFileStorageService storageService,
                                 ObjectMapper objectMapper) {
        this.complaintMediaRepository = complaintMediaRepository;
        this.imageEmbeddingRepository = imageEmbeddingRepository;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void embedAllMedia(Long complaintId) {
        List<ComplaintMedia> mediaList = complaintMediaRepository.findByComplaintIdOrderByUploadedAtAsc(complaintId);
        for (ComplaintMedia media : mediaList) {
            if (imageEmbeddingRepository.existsByMediaIdAndModelName(media.getId(), MODEL_NAME)) {
                continue;
            }
            try {
                BufferedImage image = readImage(media);
                if (image == null) {
                    log.info("Skipping embedding for media {}: image format not decodable", media.getId());
                    continue;
                }
                double[] vector = computeEmbedding(image);
                imageEmbeddingRepository.save(new ImageEmbedding(
                        media.getId(), MODEL_NAME, MODEL_VERSION,
                        objectMapper.writeValueAsString(vector)));
            } catch (Exception e) {
                log.warn("Could not embed media {} of complaint {}: {}", media.getId(), complaintId, e.getMessage());
            }
        }
    }

    private BufferedImage readImage(ComplaintMedia media) throws IOException {
        Resource resource = storageService.load(media.getObjectKey());
        try (InputStream in = resource.getInputStream()) {
            return ImageIO.read(in);
        }
    }

    /**
     * Deterministic 128-dim local baseline embedding (NOT a neural model):
     * 64 values = 8x8 average-pooled grayscale thumbnail,
     * 64 values = 4x4x4 RGB histogram of the same thumbnail,
     * L2-normalized. Useful for cosine similarity between photos; a real
     * embedding model can be plugged in behind the same storage format.
     */
    public static double[] computeEmbedding(BufferedImage image) {
        BufferedImage thumb = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumb.createGraphics();
        try {
            g.drawImage(image, 0, 0, 8, 8, null);
        } finally {
            g.dispose();
        }

        double[] vector = new double[DIMENSIONS];
        int[][][] histogram = new int[4][4][4];
        int index = 0;
        int[] gray = new int[64];
        int grayIndex = 0;

        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int rgb = thumb.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int gr = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int luminance = (r + gr + b) / 3;
                gray[grayIndex++] = luminance;
                histogram[r / 64][gr / 64][b / 64]++;
            }
        }

        for (int i = 0; i < 64; i++) {
            vector[index++] = gray[i] / 255.0;
        }
        for (int r = 0; r < 4; r++) {
            for (int gr = 0; gr < 4; gr++) {
                for (int b = 0; b < 4; b++) {
                    vector[index++] = histogram[r][gr][b] / 64.0;
                }
            }
        }

        double norm = 0.0;
        for (double v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
        return vector;
    }

    public static double cosineSimilarity(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) {
            throw new IllegalArgumentException("Embedding vectors must be non-null and equal length");
        }
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }
}
