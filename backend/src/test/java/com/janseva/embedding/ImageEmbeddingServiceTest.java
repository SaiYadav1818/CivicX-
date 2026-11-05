package com.janseva.embedding;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageEmbeddingServiceTest {

    private BufferedImage gradient(int width, int height, long seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        Random random = new Random(seed);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int r = (x * 255) / Math.max(1, width - 1);
                int gr = (y * 255) / Math.max(1, height - 1);
                int b = random.nextInt(64) + 96;
                g.setColor(new Color(r, gr, b));
                g.drawLine(x, y, x, y);
            }
        }
        g.dispose();
        return image;
    }

    private BufferedImage noise(int width, int height, long seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        Random random = new Random(seed);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int v = random.nextInt(256);
                g.setColor(new Color(v, v, v));
                g.drawLine(x, y, x, y);
            }
        }
        g.dispose();
        return image;
    }

    @Test
    void embeddingHasExpectedDimensionsAndIsNormalized() {
        double[] vector = ImageEmbeddingService.computeEmbedding(gradient(128, 128, 7));

        assertThat(vector).hasSize(ImageEmbeddingService.DIMENSIONS);
        double norm = 0.0;
        for (double v : vector) {
            norm += v * v;
        }
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void sameImageIsPerfectCosineMatch() {
        double[] a = ImageEmbeddingService.computeEmbedding(gradient(128, 128, 1));
        double[] b = ImageEmbeddingService.computeEmbedding(gradient(128, 128, 1));

        assertThat(ImageEmbeddingService.cosineSimilarity(a, b)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void resizedSameVisualIsHighlySimilar() {
        double[] large = ImageEmbeddingService.computeEmbedding(gradient(256, 256, 3));
        double[] small = ImageEmbeddingService.computeEmbedding(gradient(64, 64, 3));

        assertThat(ImageEmbeddingService.cosineSimilarity(large, small)).isGreaterThan(0.95);
    }

    @Test
    void unrelatedImagesAreLessSimilar() {
        double[] a = ImageEmbeddingService.computeEmbedding(noise(128, 128, 11));
        double[] b = ImageEmbeddingService.computeEmbedding(noise(128, 128, 22));

        assertThat(ImageEmbeddingService.cosineSimilarity(a, b)).isLessThan(0.85);
    }

    @Test
    void cosineRejectsMismatchedVectors() {
        assertThatThrownBy(() -> ImageEmbeddingService.cosineSimilarity(new double[10], new double[11]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
