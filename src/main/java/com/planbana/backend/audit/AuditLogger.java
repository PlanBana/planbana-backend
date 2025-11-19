package com.planbana.backend.audit;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuditLogger {

    private final AuditLogRepository repo;

    public AuditLogger(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void log(AuditCategory category,
            AuditAction action,
            String performedBy,
            String targetUser,
            String eventId,
            Map<String, Object> meta) {

        AuditLog log = new AuditLog(
                category,
                action,
                performedBy,
                targetUser,
                eventId,
                meta);

        repo.save(log);
    }

    // Convenience wrappers (optional – use or ignore)
    public void event(AuditAction action,
            String performedBy,
            String targetUser,
            String eventId,
            Map<String, Object> meta) {
        log(AuditCategory.EVENT_LIFECYCLE, action, performedBy, targetUser, eventId, meta);
    }

    public void security(AuditAction action,
            String performedBy,
            Map<String, Object> meta) {
        log(AuditCategory.SECURITY_AUTH, action, performedBy, null, null, meta);
    }
}
