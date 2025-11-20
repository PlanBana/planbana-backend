package com.planbana.backend.audit;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class AuditLogger {

    private final AuditLogRepository repo;

    public AuditLogger(AuditLogRepository repo) {
        this.repo = repo;
    }

    // ============================================================
    // MAIN log() — matches your AuditLog constructor EXACTLY
    // ============================================================

    public void log(
            AuditCategory category,
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

        // timestamp already assigned inside constructor
        repo.save(log);
    }

    // ============================================================
    // Overload without meta
    // ============================================================

    public void log(
            AuditCategory category,
            AuditAction action,
            String performedBy,
            String targetUser,
            String eventId) {
        log(category, action, performedBy, targetUser, eventId, Map.of());
    }

    // ============================================================
    // SECURITY logs (auto-category)
    // ============================================================

    public void security(
            AuditAction action,
            String performedBy,
            Map<String, Object> meta) {

        AuditLog log = new AuditLog(
                AuditCategory.SECURITY_AUTH,
                action,
                performedBy,
                null,
                null,
                meta);

        repo.save(log);
    }

    public void security(
            AuditAction action,
            String performedBy) {
        security(action, performedBy, Map.of());
    }

    // ============================================================
    // SYSTEM logs (auto-category)
    // ============================================================

    public void system(
            AuditAction action,
            String performedBy,
            Map<String, Object> meta) {

        AuditLog log = new AuditLog(
                AuditCategory.SYSTEM_SETTINGS,
                action,
                performedBy,
                null,
                null,
                meta);

        repo.save(log);
    }

    public void system(
            AuditAction action,
            String performedBy) {
        system(action, performedBy, Map.of());
    }

}
