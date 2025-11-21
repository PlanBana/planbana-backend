package com.planbana.backend.audit;

import com.planbana.backend.admin.activity.AdminActivityFeedService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuditLogger {

    private final AuditLogRepository repo;
    private final AdminActivityFeedService activityFeed;

    public AuditLogger(AuditLogRepository repo,
            AdminActivityFeedService activityFeed) {
        this.repo = repo;
        this.activityFeed = activityFeed;
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

        // Persist
        repo.save(log);

        // Push to SSE feed (best-effort, never break request)
        try {
            activityFeed.publishAudit(log);
        } catch (Exception ignored) {
        }
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

        try {
            activityFeed.publishAudit(log);
        } catch (Exception ignored) {
        }
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

        try {
            activityFeed.publishAudit(log);
        } catch (Exception ignored) {
        }
    }

    public void system(
            AuditAction action,
            String performedBy) {
        system(action, performedBy, Map.of());
    }

}
