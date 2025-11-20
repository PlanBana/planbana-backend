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
import java.util.List;
import java.util.Map;

@Service
public class AutoModerationService {

    private final EventRepository eventRepo;
    private final UserRepository userRepo;
    private final AuditLogger auditLogger;

    // Configurable thresholds
    private static final int REPORT_THRESHOLD = 5;
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

        if (reportCount < REPORT_THRESHOLD)
            return;

        // Already blocked? skip
        if (event.getStatus() == Event.Status.BLOCKED)
            return;

        Event.Status oldStatus = event.getStatus();
        event.setStatus(Event.Status.BLOCKED);
        eventRepo.save(event);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.EVENT_AUTO_BLOCKED,
                "SYSTEM",
                event.getCreatedByUserId(),
                event.getId(),
                Map.of(
                        "reason", "REPORT_THRESHOLD_REACHED",
                        "oldStatus", oldStatus.name(),
                        "newStatus", "BLOCKED",
                        "reports", reportCount));

        auditLogger.log(
                AuditCategory.FRAUD,
                AuditAction.REPORT_THRESHOLD_REACHED,
                "SYSTEM",
                event.getCreatedByUserId(),
                event.getId(),
                Map.of("reports", reportCount));
    }

    // =========================================================================
    // 2️⃣ AUTO-HIDE EVENT (spam or inappropriate content)
    // =========================================================================
    public void autoHideSuspiciousEvent(Event event, String reason) {

        if (event.getStatus() == Event.Status.HIDDEN)
            return;

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
                        "oldStatus", oldStatus.name(),
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
    // 3️⃣ SPAM DETECTION
    // =========================================================================
    public boolean isSpam(Event event) {
        String text = (event.getDescription() == null ? "" : event.getDescription()).toLowerCase();

        if (text.length() < SPAM_DESCRIPTION_MIN_LENGTH)
            return true;

        String[] tokens = text.split("\\s+");

        // Count repeated words
        int repeatCount = 0;
        for (String token : tokens) {
            if (text.chars().filter(ch -> ch == token.charAt(0)).count() > 10) {
                repeatCount++;
            }
        }

        return repeatCount >= SPAM_REPEAT_KEYWORD_COUNT;
    }

    // =========================================================================
    // 4️⃣ AI-BASED AUTO REJECTION OF KYC
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
                        "oldStatus", oldStatus.name(),
                        "newStatus", "REJECTED",
                        "reason", reason,
                        "aiMeta", aiMeta));
    }

    // =========================================================================
    // 5️⃣ AI FLAGGING → triggers moderation queue
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
    }

    // =========================================================================
    // 6️⃣ CRON: run background auto-moderation checks
    // =========================================================================
    public void runBackgroundChecks() {
        List<Event> events = eventRepo.findAll();

        for (Event e : events) {
            boolean spam = isSpam(e);

            if (spam) {
                autoHideSuspiciousEvent(e, "AI_SPAM_DETECTED");
            }
        }
    }
}
