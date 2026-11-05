package com.janseva.hashing;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageHashServiceTest {

    @Test
    void sha256IsDeterministicAndMatchesKnownVector() {
        String hash = ImageHashService.sha256Hex("abc".getBytes(StandardCharsets.UTF_8));

        assertThat(hash).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(ImageHashService.sha256Hex("abc".getBytes(StandardCharsets.UTF_8))).isEqualTo(hash);
    }

    private BufferedImage stripes(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = image.createGraphics();
        int stripeWidth = Math.max(1, width / 8);
        for (int x = 0; x < width; x++) {
            int brightness = (x / stripeWidth) * 32;
            g.setColor(new Color(brightness, brightness, brightness));
            g.drawLine(x, 0, x, height);
        }
        g.dispose();
        return image;
    }

    private BufferedImage noise(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = image.createGraphics();
        java.util.Random random = new java.util.Random(42);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int v = random.nextInt(256);
                g.setColor(new Color(v, v, v));
                g.drawLine(x, y, x, y);
            }
        }
        g.dispose();
        return image;
    }

    @Test
    void sameImageHasZeroHammingDistance() {
        String h1 = ImageHashService.perceptualHash(stripes(64, 64), true);
        String h2 = ImageHashService.perceptualHash(stripes(64, 64), true);

        assertThat(ImageHashService.hammingDistance(h1, h2)).isZero();
    }

    @Test
    void resizedImageIsDetectedAsSimilarByAverageHash() {
        String h64 = ImageHashService.perceptualHash(stripes(64, 64), true);
        String h32 = ImageHashService.perceptualHash(stripes(32, 32), true);

        assertThat(ImageHashService.hammingDistance(h64, h32)).isZero();
    }

    @Test
    void differentImagesAreNotAutomaticallySimilar() {
        String a = ImageHashService.perceptualHash(stripes(64, 64), true);
        String b = ImageHashService.perceptualHash(noise(64, 64), true);

        assertThat(ImageHashService.hammingDistance(a, b)).isGreaterThan(10);
    }

    @Test
    void differenceHashIsStable() {
        String d1 = ImageHashService.perceptualHash(stripes(64, 64), false);
        String d2 = ImageHashService.perceptualHash(stripes(64, 64), false);

        assertThat(ImageHashService.hammingDistance(d1, d2)).isZero();
    }

    @Test
    void invalidImageBytesYieldNullPerceptualHash() {
        byte[] garbage = "not an image".getBytes(StandardCharsets.UTF_8);

        assertThat(ImageHashService.perceptualHash(garbage, true)).isNull();
    }
}
