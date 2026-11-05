package com.janseva.hashing;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class ImageHashService {

    private static final Logger log = LoggerFactory.getLogger(ImageHashService.class);
    private static final int HASH_SIZE = 8;

    private final ComplaintMediaRepository complaintMediaRepository;
    private final ImageHashRepository imageHashRepository;
    private final ComplaintFileStorageService storageService;

    public ImageHashService(ComplaintMediaRepository complaintMediaRepository,
                            ImageHashRepository imageHashRepository,
                            ComplaintFileStorageService storageService) {
        this.complaintMediaRepository = complaintMediaRepository;
        this.imageHashRepository = imageHashRepository;
        this.storageService = storageService;
    }

    @Transactional
    public void hashAllMedia(Long complaintId) {
        List<ComplaintMedia> mediaList = complaintMediaRepository.findByComplaintIdOrderByUploadedAtAsc(complaintId);
        for (ComplaintMedia media : mediaList) {
            if (imageHashRepository.existsByMediaId(media.getId())) {
                continue;
            }
            try {
                byte[] data = loadBytes(media);
                String sha256 = sha256Hex(data);
                String phash = perceptualHash(data, true);
                String dhash = perceptualHash(data, false);
                imageHashRepository.save(new ImageHash(media.getId(), sha256, phash, dhash));
            } catch (Exception e) {
                log.warn("Could not hash media {} of complaint {}: {}", media.getId(), complaintId, e.getMessage());
            }
        }
    }

    private byte[] loadBytes(ComplaintMedia media) throws IOException {
        Resource resource = storageService.load(media.getObjectKey());
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        }
    }

    public static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * 64-bit perceptual hash stored as 16 hex chars.
     * average=true computes the classic average hash on an 8x8 grayscale thumbnail;
     * average=false computes a gradient (difference) hash on 9x8.
     * Returns null when the image cannot be decoded.
     */
    public static String perceptualHash(byte[] data, boolean average) {
        BufferedImage original;
        try {
            original = ImageIO.read(new java.io.ByteArrayInputStream(data));
        } catch (IOException e) {
            return null;
        }
        if (original == null) {
            return null;
        }
        return perceptualHash(original, average);
    }

    public static String perceptualHash(BufferedImage image, boolean average) {
        if (average) {
            BufferedImage gray = resizeGray(image, HASH_SIZE, HASH_SIZE);
            int total = 0;
            int[] pixels = new int[HASH_SIZE * HASH_SIZE];
            for (int y = 0; y < HASH_SIZE; y++) {
                for (int x = 0; x < HASH_SIZE; x++) {
                    int p = gray.getRGB(x, y) & 0xFF;
                    pixels[y * HASH_SIZE + x] = p;
                    total += p;
                }
            }
            int mean = total / pixels.length;
            long bits = 0;
            for (int i = 0; i < pixels.length; i++) {
                if (pixels[i] >= mean) {
                    bits |= (1L << (63 - i));
                }
            }
            return toHex(bits);
        }

        BufferedImage gray = resizeGray(image, HASH_SIZE + 1, HASH_SIZE);
        long bits = 0;
        int index = 0;
        for (int y = 0; y < HASH_SIZE; y++) {
            for (int x = 0; x < HASH_SIZE; x++) {
                int left = gray.getRGB(x, y) & 0xFF;
                int right = gray.getRGB(x + 1, y) & 0xFF;
                if (left > right) {
                    bits |= (1L << (63 - index));
                }
                index++;
            }
        }
        return toHex(bits);
    }

    private static BufferedImage resizeGray(BufferedImage source, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = resized.createGraphics();
        try {
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return resized;
    }

    private static String toHex(long bits) {
        return String.format("%016x", bits);
    }

    public static int hammingDistance(String hexA, String hexB) {
        if (hexA == null || hexB == null || hexA.length() != 16 || hexB.length() != 16) {
            return Integer.MAX_VALUE;
        }
        long a = Long.parseUnsignedLong(hexA, 16);
        long b = Long.parseUnsignedLong(hexB, 16);
        return Long.bitCount(a ^ b);
    }
}
