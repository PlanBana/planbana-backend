package com.planbana.backend.moderation;

import com.planbana.backend.audit.AuditAction;
import com.planbana.backend.audit.AuditCategory;
import com.planbana.backend.audit.AuditLogger;
import com.planbana.backend.events.Event;
import com.planbana.backend.events.EventRepository;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
public class AutoModerationService {

    private final EventRepository eventRepo;
    private final UserRepository userRepo;
    private final AuditLogger auditLogger;

    // Simple spam heuristics; tunable
    private static final int SPAM_DESCRIPTION_MIN_LENGTH = 20;
    private static final int SPAM_REPEAT_KEYWORD_COUNT = 3;

    public AutoModerationService(EventRepository eventRepo,
            UserRepository userRepo,
            AuditLogger auditLogger) {
        this.eventRepo = eventRepo;
        this.userRepo = userRepo;
        this.auditLogger = auditLogger;
    }

    // =========================================================================
    // 1️⃣ AUTO-BLOCK EVENT (when reports exceed threshold)
    // =========================================================================
    public void autoBlockEventIfThresholdReached(Event event, int reportCount) {

        if (!ModerationRules.AUTO_BLOCK_ENABLED) {
            return;
        }

        if (reportCount < ModerationRules.REPORT_THRESHOLD) {
            return;
        }

        // Already blocked? skip
        if (event.getStatus() == Event.Status.BLOCKED) {
            return;
        }

        Event.Status oldStatus = event.getStatus();
        event.setStatus(Event.Status.BLOCKED);
        eventRepo.save(event);

        // Admin-style log: event got blocked
        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.EVENT_AUTO_BLOCKED,
                "SYSTEM",
                event.getCreatedByUserId(),
                event.getId(),
                Map.of(
                        "reason", "REPORT_THRESHOLD_REACHED",
                        "oldStatus", oldStatus != null ? oldStatus.name() : null,
                        "newStatus", "BLOCKED",
                        "reports", reportCount,
                        "threshold", ModerationRules.REPORT_THRESHOLD));

        // Fraud / automation log for analytics
        auditLogger.log(
                AuditCategory.FRAUD,
                AuditAction.REPORT_THRESHOLD_REACHED,
                "SYSTEM",
                event.getCreatedByUserId(),
                event.getId(),
                Map.of(
                        "reports", reportCount,
                        "threshold", ModerationRules.REPORT_THRESHOLD));
    }

    // =========================================================================
    // 2️⃣ AUTO-HIDE EVENT (spam or inappropriate content)
    // =========================================================================
    public void autoHideSuspiciousEvent(Event event, String reason) {

        if (!ModerationRules.AUTO_SPAM_HIDE_ENABLED) {
            return;
        }

        if (event.getStatus() == Event.Status.HIDDEN) {
            return;
        }

        Event.Status oldStatus = event.getStatus();
        event.setStatus(Event.Status.HIDDEN);
        eventRepo.save(event);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.ADMIN_HIDDEN_EVENT,
                "SYSTEM",
                event.getCreatedByUserId(),
                event.getId(),
                Map.of(
                        "reason", reason,
                        "oldStatus", oldStatus != null ? oldStatus.name() : null,
                        "newStatus", "HIDDEN"));

        auditLogger.log(
                AuditCategory.FRAUD,
                AuditAction.SUSPICIOUS_EVENT_SPAM,
                "SYSTEM",
                event.getCreatedByUserId(),
                event.getId(),
                Map.of("reason", reason));
    }

    // =========================================================================
    // 3️⃣ SPAM DETECTION – simple heuristic
    // =========================================================================
    public boolean isSpam(Event event) {
        String text = (event.getDescription() == null ? "" : event.getDescription()).toLowerCase();

        // Very short description -> suspicious
        if (text.length() < SPAM_DESCRIPTION_MIN_LENGTH) {
            return true;
        }

        String[] tokens = text.split("\\s+");

        // Very naive: if many characters repeated, treat as spam
        int repeatCount = 0;
        for (String token : tokens) {
            if (token.isEmpty())
                continue;
            long occurrences = text.chars()
                    .filter(ch -> ch == token.charAt(0))
                    .count();
            if (occurrences > 10) {
                repeatCount++;
            }
        }

        return repeatCount >= SPAM_REPEAT_KEYWORD_COUNT;
    }

    // =========================================================================
    // 4️⃣ AUTO-ARCHIVE OLD EVENTS
    // =========================================================================
    /**
     * Auto-archive (= mark as canceled) events whose endAt is older than
     * AUTO_ARCHIVE_DAYS_AFTER_END days.
     *
     * We are using `isCanceled = true` as the archived flag to avoid introducing
     * a new Event.Status value that might break existing code.
     */
    public void autoArchiveOldEvents() {

        if (!ModerationRules.AUTO_ARCHIVE_ENABLED) {
            return;
        }

        Instant now = Instant.now();
        Instant cutoff = now.minus(ModerationRules.AUTO_ARCHIVE_DAYS_AFTER_END, ChronoUnit.DAYS);

        List<Event> events = eventRepo.findAll();

        for (Event e : events) {
            if (e.getEndAt() == null) {
                continue;
            }
            if (e.isCanceled()) {
                continue;
            }

            // ended long ago?
            if (e.getEndAt().isBefore(cutoff)) {
                boolean oldCanceled = e.isCanceled();

                e.setCanceled(true);
                eventRepo.save(e);

                auditLogger.log(
                        AuditCategory.EVENT_LIFECYCLE,
                        AuditAction.EVENT_AUTO_ARCHIVED,
                        "SYSTEM",
                        e.getCreatedByUserId(),
                        e.getId(),
                        Map.of(
                                "oldCanceledState", oldCanceled,
                                "newCanceledState", true,
                                "endAt", e.getEndAt().toString(),
                                "cutoffDays", ModerationRules.AUTO_ARCHIVE_DAYS_AFTER_END));
            }
        }
    }

    // =========================================================================
    // 5️⃣ AI-BASED AUTO REJECTION OF KYC
    // =========================================================================
    public void rejectKycAutomatically(User user, String reason, Map<String, Object> aiMeta) {

        User.VerificationStatus oldStatus = user.getGovIdVerificationStatus();

        user.setGovIdVerificationStatus(User.VerificationStatus.REJECTED);
        userRepo.save(user);

        auditLogger.log(
                AuditCategory.FRAUD,
                AuditAction.AUTOMATION_KYC_AUTO_REJECTED,
                "SYSTEM",
                user.getId(),
                null,
                Map.of(
                        "oldStatus", oldStatus != null ? oldStatus.name() : null,
                        "newStatus", "REJECTED",
                        "reason", reason,
                        "aiMeta", aiMeta));
    }

    // =========================================================================
    // 6️⃣ AI FLAGGING → triggers moderation queue (AdminReports)
    // =========================================================================
    public void flagEventAI(Event event, Map<String, Object> prediction) {

        auditLogger.log(
                AuditCategory.FRAUD,
                AuditAction.AUTOMATION_AI_FLAGGED,
                "SYSTEM",
                event.getCreatedByUserId(),
                event.getId(),
                Map.of(
                        "aiPrediction", prediction,
                        "timestamp", Instant.now().toString()));
        // The actual queue is built on EventReport + AdminReportsController.
        // This log helps admins see AI flags in audit feed.
    }

    // =========================================================================
    // 7️⃣ CRON: run background auto-moderation checks
    // =========================================================================
    /**
     * Background checks: spam detection auto-hide.
     * Archiving is done in a separate method so cron can call both.
     */
    public void runBackgroundChecks() {
        if (!ModerationRules.CRON_ENABLED) {
            return;
        }

        List<Event> events = eventRepo.findAll();

        for (Event e : events) {
            if (!ModerationRules.AUTO_SPAM_HIDE_ENABLED) {
                continue;
            }

            boolean spam = isSpam(e);

            if (spam) {
                autoHideSuspiciousEvent(e, "AI_SPAM_DETECTED_BACKGROUND");
            }
        }
    }
}
