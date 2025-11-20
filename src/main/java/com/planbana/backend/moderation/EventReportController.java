package com.planbana.backend.moderation;

import com.planbana.backend.audit.AuditAction;
import com.planbana.backend.audit.AuditCategory;
import com.planbana.backend.audit.AuditLogger;
import com.planbana.backend.events.Event;
import com.planbana.backend.events.EventRepository;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/events/{eventId}/report")
public class EventReportController {

        private final EventRepository eventRepo;
        private final UserRepository userRepo;
        private final EventReportService reportService;
        private final AuditLogger auditLogger;

        public EventReportController(
                        EventRepository eventRepo,
                        UserRepository userRepo,
                        EventReportService reportService,
                        AuditLogger auditLogger) {
                this.eventRepo = eventRepo;
                this.userRepo = userRepo;
                this.reportService = reportService;
                this.auditLogger = auditLogger;
        }

        // ================================
        // DTO
        // ================================

        public static class ReportRequest {
                @NotBlank
                @Size(min = 5, max = 500)
                public String reason;

                public String category; // optional (e.g., "spam", "abuse", "misleading")
        }

        // ================================
        // POST /api/events/{eventId}/report
        // ================================

        @PostMapping
        public Map<String, Object> report(
                        @PathVariable String eventId,
                        @RequestBody ReportRequest req,
                        Authentication auth) {
                if (auth == null || auth.getName() == null) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
                }

                User reporter = userRepo.findByPhone(auth.getName())
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                                                "User not found"));

                Event event = eventRepo.findById(eventId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Event not found"));

                // Prevent host reporting own event
                if (event.isHost(reporter.getId())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Host cannot report their own event");
                }

                // Prevent duplicate reports from same user
                boolean alreadyReported = reportService.hasUserAlreadyReported(eventId, reporter.getId());
                if (alreadyReported) {
                        return Map.of(
                                        "status", "ALREADY_REPORTED",
                                        "message", "You have already reported this event");
                }

                // Save report
                EventReport report = reportService.saveReport(
                                eventId,
                                reporter.getId(),
                                req.reason,
                                req.category);

                // Audit log (user action)
                auditLogger.log(
                                AuditCategory.EVENT_LIFECYCLE,
                                AuditAction.USER_REPORTED_EVENT,
                                reporter.getId(),
                                event.getCreatedByUserId(),
                                eventId,
                                Map.of(
                                                "reason", req.reason,
                                                "category", req.category,
                                                "timestamp", Instant.now().toString()));

                // Auto-Moderation hook
                reportService.evaluateAutoModeration(eventId);

                return Map.of(
                                "status", "REPORTED",
                                "message", "Report submitted successfully",
                                "reportId", report.getId());
        }
}
