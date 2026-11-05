package com.janseva.complaint;

import com.janseva.audit.AuditService;
import com.janseva.common.ApiException;
import com.janseva.config.AppProperties;
import com.janseva.security.CurrentUser;
import com.janseva.storage.ComplaintFileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Service
public class ComplaintMediaService {

    private static final Logger log = LoggerFactory.getLogger(ComplaintMediaService.class);

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] WEBP_MAGIC = {'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'};

    private final ComplaintRepository complaintRepository;
    private final ComplaintMediaRepository complaintMediaRepository;
    private final ComplaintFileStorageService storageService;
    private final AuditService auditService;
    private final AppProperties properties;

    public ComplaintMediaService(ComplaintRepository complaintRepository,
                                 ComplaintMediaRepository complaintMediaRepository,
                                 ComplaintFileStorageService storageService,
                                 AuditService auditService,
                                 AppProperties properties) {
        this.complaintRepository = complaintRepository;
        this.complaintMediaRepository = complaintMediaRepository;
        this.storageService = storageService;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Transactional
    public ComplaintMedia upload(Long complaintId, Long userId, MultipartFile file) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found"));

        boolean isOwner = complaint.getCitizen().getId().equals(userId);
        boolean isStaff = CurrentUser.hasRole("ADMIN") || CurrentUser.hasRole("SUPERVISOR");
        if (!isOwner && !isStaff) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only upload images to your own complaints");
        }

        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image file is required");
        }
        if (file.getSize() > properties.storage().maxImageSizeBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Image exceeds the maximum allowed size of " + properties.storage().maxImageSizeBytes() + " bytes");
        }

        long existing = complaintMediaRepository.countByComplaintId(complaintId);
        if (existing >= properties.storage().maxImagesPerComplaint()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Maximum number of images (" + properties.storage().maxImagesPerComplaint() + ") reached for this complaint");
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read uploaded file");
        }

        ImageFormat format = detectFormat(data);
        String objectKey = storageService.store(
                String.valueOf(complaintId), format.extension, new ByteArrayInputStream(data), data.length);

        ComplaintMedia media = new ComplaintMedia(
                complaint, objectKey, sanitizeFilename(file.getOriginalFilename()),
                format.contentType, data.length);
        captureDimensions(media, data, format);
        media.setProcessingStatus(MediaProcessingStatus.COMPLETED);
        complaintMediaRepository.save(media);

        auditService.log(userId, AuditService.IMAGE_UPLOADED, "COMPLAINT_MEDIA",
                String.valueOf(media.getId()),
                Map.of("complaintNumber", complaint.getComplaintNumber(),
                        "contentType", format.contentType,
                        "sizeBytes", data.length));

        return media;
    }

    @Transactional(readOnly = true)
    public ComplaintMedia getMediaForDownload(Long mediaId, Long userId) {
        ComplaintMedia media = complaintMediaRepository.findById(mediaId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Image not found"));
        boolean isOwner = media.getComplaint().getCitizen().getId().equals(userId);
        boolean isStaff = CurrentUser.hasRole("ADMIN") || CurrentUser.hasRole("SUPERVISOR");
        if (!isOwner && !isStaff) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this image");
        }
        return media;
    }

    private ImageFormat detectFormat(byte[] data) {
        if (startsWith(data, JPEG_MAGIC)) {
            return ImageFormat.JPEG;
        }
        if (startsWith(data, PNG_MAGIC)) {
            return ImageFormat.PNG;
        }
        if (startsWith(data, WEBP_MAGIC)) {
            return ImageFormat.WEBP;
        }
        throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported image format. Only JPEG, PNG and WebP are accepted.");
    }

    private boolean startsWith(byte[] data, byte[] magic) {
        if (data.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            byte expected = magic[i];
            if (expected != 0x00 && data[i] != expected) {
                return false;
            }
        }
        return true;
    }

    private String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return null;
        }
        String base = original.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.length() > 255) {
            base = base.substring(base.length() - 255);
        }
        return base;
    }

    private void captureDimensions(ComplaintMedia media, byte[] data, ImageFormat format) {
        if (format == ImageFormat.WEBP) {
            return;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image != null) {
                media.setWidth(image.getWidth());
                media.setHeight(image.getHeight());
            }
        } catch (IOException e) {
            log.debug("Could not read image dimensions for media", e);
        }
    }

    private enum ImageFormat {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        WEBP("webp", "image/webp");

        final String extension;
        final String contentType;

        ImageFormat(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }
    }
}
