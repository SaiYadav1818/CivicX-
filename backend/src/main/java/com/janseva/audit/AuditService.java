package com.janseva.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AuditService {

    public static final String COMPLAINT_CREATED = "COMPLAINT_CREATED";
    public static final String IMAGE_UPLOADED = "IMAGE_UPLOADED";
    public static final String COMPLAINT_VERIFIED = "COMPLAINT_VERIFIED";
    public static final String COMPLAINT_REJECTED = "COMPLAINT_REJECTED";
    public static final String STATUS_CHANGED = "STATUS_CHANGED";

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String action, String entityType, String entityId,
                    Map<String, Object> details, String ipAddress) {
        auditLogRepository.save(new AuditLog(userId, action, entityType, entityId, details, ipAddress));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String action, String entityType, String entityId, Map<String, Object> details) {
        log(userId, action, entityType, entityId, details, null);
    }
}
