package com.janseva.storage;

import com.janseva.common.ApiException;
import com.janseva.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class LocalComplaintFileStorageService implements ComplaintFileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalComplaintFileStorageService.class);
    private static final Pattern SAFE_KEY = Pattern.compile("^[a-zA-Z0-9/._-]+$");

    private final Path root;

    public LocalComplaintFileStorageService(AppProperties properties) {
        this.root = Paths.get(properties.storage().localDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create storage directory: " + root, e);
        }
        log.info("Local complaint file storage initialized at {}", root);
    }

    @Override
    public String store(String complaintId, String extension, InputStream content, long contentLength) {
        String key = "complaints/" + complaintId + "/" + UUID.randomUUID() + "." + extension;
        validateKey(key);
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid storage key");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to store file {}", key, e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store file");
        }
        return key;
    }

    @Override
    public Resource load(String objectKey) {
        validateKey(objectKey);
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Image not found");
        }
        try {
            return new UrlResource(target.toUri());
        } catch (MalformedURLException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Image not found");
        }
    }

    @Override
    public void delete(String objectKey) {
        validateKey(objectKey);
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Could not delete file {}", objectKey, e);
        }
    }

    private void validateKey(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches() || key.contains("..")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid storage key");
        }
    }
}
