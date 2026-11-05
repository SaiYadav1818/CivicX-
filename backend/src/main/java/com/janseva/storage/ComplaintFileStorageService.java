package com.janseva.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;

public interface ComplaintFileStorageService {

    String store(String complaintId, String extension, InputStream content, long contentLength);

    Resource load(String objectKey);

    void delete(String objectKey);
}
