package com.planbana.backend.admin.reports;

import com.planbana.backend.audit.AuditAction;
import com.planbana.backend.audit.AuditCategory;
import com.planbana.backend.audit.AuditLogger;
import com.planbana.backend.events.Event;
import com.planbana.backend.events.EventRepository;
import com.planbana.backend.moderation.EventReport;
import com.planbana.backend.moderation.EventReportService;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportsController {

    private final EventReportService reportService;
    private final EventRepository eventRepo;
    private final UserRepository userRepo;
    private final AuditLogger auditLogger;

    public AdminReportsController(
            EventReportService reportService,
            EventRepository eventRepo,
            UserRepository userRepo,
            AuditLogger auditLogger) {
        this.reportService = reportService;
        this.eventRepo = eventRepo;
        this.userRepo = userRepo;
        this.auditLogger = auditLogger;
    }

    // ============================================================
    // 1️⃣ LIST REPORTS (paged + filters)
    // ============================================================

    @GetMapping
    public Page<EventReport> listReports(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestParam(defaultValue = "createdAt") String sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        return reportService.searchReports(status, category, pageable);
    }

    // ============================================================
    // 2️⃣ GET SINGLE REPORT DETAIL
    // ============================================================

    @GetMapping("/{reportId}")
    public EventReport getReport(@PathVariable String reportId) {
        return reportService.getReport(reportId);
    }

    // ============================================================
    // 3️⃣ REVIEW REPORT (mark as reviewed)
    // ============================================================

    @PostMapping("/{reportId}/review")
    public Map<String, String> reviewReport(
            @PathVariable String reportId,
            @RequestParam String adminId) {
        EventReport report = reportService.getReport(reportId);
        report.setStatus(EventReport.Status.REVIEWED);
        report.setReviewedBy(adminId);
        report.setReviewedAt(Instant.now());
        reportService.save(report);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.REPORT_REVIEWED_BY_ADMIN,
                adminId,
                report.getReporterUserId(),
                report.getEventId(),
                Map.of("status", "REVIEWED"));

        return Map.of("message", "Report reviewed");
    }

    // ============================================================
    // 4️⃣ TAKE MODERATION ACTION (block event, hide event, warn user)
    // ============================================================

    public static class ModerationActionRequest {
        public String action; // BLOCK_EVENT, HIDE_EVENT, WARN_USER
        public String adminId;
        public String reason;
    }

    @PostMapping("/{reportId}/action")
    public Map<String, Object> moderate(
            @PathVariable String reportId,
            @RequestBody ModerationActionRequest req) {
        EventReport report = reportService.getReport(reportId);
        Event event = eventRepo.findById(report.getEventId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

        String adminId = req.adminId;
        String reporterId = report.getReporterUserId();

        switch (req.action.toUpperCase()) {

            case "BLOCK_EVENT" -> {
                event.setStatus(Event.Status.BLOCKED);
                eventRepo.save(event);

                auditLogger.log(
                        AuditCategory.ADMIN_EVENTS,
                        AuditAction.ADMIN_BLOCKED_EVENT,
                        adminId,
                        event.getCreatedByUserId(),
                        event.getId(),
                        Map.of("reason", req.reason, "reportId", reportId));
            }

            case "HIDE_EVENT" -> {
                event.setStatus(Event.Status.HIDDEN);
                eventRepo.save(event);

                auditLogger.log(
                        AuditCategory.ADMIN_EVENTS,
                        AuditAction.ADMIN_HIDDEN_EVENT,
                        adminId,
                        event.getCreatedByUserId(),
                        event.getId(),
                        Map.of("reason", req.reason, "reportId", reportId));
            }

            case "WARN_USER" -> {
                // Optional: Add warning count to user model later
                auditLogger.log(
                        AuditCategory.ADMIN_USERS,
                        AuditAction.REPORT_ACTION_TAKEN,
                        adminId,
                        event.getCreatedByUserId(),
                        event.getId(),
                        Map.of("reason", req.reason, "type", "WARN_USER"));
            }

            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid action. Allowed: BLOCK_EVENT, HIDE_EVENT, WARN_USER");
        }

        report.setStatus(EventReport.Status.ACTION_TAKEN);
        report.setAdminAction(req.action);
        report.setActionReason(req.reason);
        report.setReviewedBy(adminId);
        report.setReviewedAt(Instant.now());
        reportService.save(report);

        return Map.of(
                "message", "Moderation action applied",
                "newEventStatus", event.getStatus().name());
    }

    // ============================================================
    // 5️⃣ ASSIGN MODERATOR
    // ============================================================

    @PostMapping("/{reportId}/assign")
    public Map<String, String> assignModerator(
            @PathVariable String reportId,
            @RequestParam String adminId) {
        EventReport report = reportService.getReport(reportId);
        report.setAssignedTo(adminId);
        reportService.save(report);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_REVIEWED_REPORT,
                adminId,
                report.getReporterUserId(),
                report.getEventId(),
                Map.of("assignedTo", adminId));

        return Map.of("message", "Assigned successfully");
    }
}
