package com.planbana.backend.admin.reports;

import com.planbana.backend.audit.*;
import com.planbana.backend.events.Event;
import com.planbana.backend.events.EventRepository;
import com.planbana.backend.moderation.EventReport;
import com.planbana.backend.moderation.EventReportService;
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
    // Helper — Admin identity from header
    // ============================================================
    private String getAdminId(String header) {
        return (header == null || header.isBlank()) ? "UNKNOWN_ADMIN" : header;
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
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        return reportService.searchReports(status, category, pageable);
    }

    // ============================================================
    // 2️⃣ PENDING REPORTS ONLY
    // ============================================================
    @GetMapping("/pending")
    public Page<EventReport> pending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return reportService.searchReports("PENDING", null, pageable);
    }

    // ============================================================
    // 3️⃣ SINGLE REPORT DETAILS
    // ============================================================
    @GetMapping("/{reportId}")
    public EventReport getReport(@PathVariable String reportId) {
        return reportService.getReport(reportId);
    }

    // ============================================================
    // 4️⃣ REPORTS FOR SPECIFIC EVENT
    // ============================================================
    @GetMapping("/event/{eventId}")
    public List<EventReport> reportsForEvent(@PathVariable String eventId) {
        return reportService.listReportsForEvent(eventId);
    }

    // ============================================================
    // 5️⃣ REVIEW REPORT (APPROVE / REJECT)
    // ============================================================
    public static class ReviewRequest {
        public String action; // APPROVE | REJECT
        public String reason;
    }

    @PostMapping("/{reportId}/review")
    public Map<String, Object> review(
            @PathVariable String reportId,
            @RequestBody ReviewRequest req,
            @RequestHeader("admin-id") String adminHeader) {

        EventReport report = reportService.getReport(reportId);
        String adminId = getAdminId(adminHeader);

        EventReport.Status newStatus = switch (req.action.toUpperCase()) {
            case "APPROVE" -> EventReport.Status.APPROVED;
            case "REJECT" -> EventReport.Status.REJECTED;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid action");
        };

        report.setStatus(newStatus);
        report.setActionReason(req.reason);
        report.setReviewedAt(Instant.now());
        report.setReviewedBy(adminId);
        reportService.save(report);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.ADMIN_REVIEWED_EVENT_REPORT,
                adminId,
                report.getReporterUserId(),
                report.getEventId(),
                Map.of("reviewAction", req.action, "reason", req.reason));

        return Map.of("status", newStatus.toString());
    }

    // ============================================================
    // 6️⃣ TAKE ACTION (BLOCK_EVENT, HIDE_EVENT, WARN_USER)
    // ============================================================
    public static class ActionRequest {
        public String action;
        public String reason;
    }

    @PostMapping("/{reportId}/action")
    public Map<String, Object> takeAction(
            @PathVariable String reportId,
            @RequestBody ActionRequest req,
            @RequestHeader("admin-id") String adminHeader) {

        EventReport report = reportService.getReport(reportId);
        String adminId = getAdminId(adminHeader);

        Event event = eventRepo.findById(report.getEventId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

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
                        Map.of("reason", req.reason));
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
                        Map.of("reason", req.reason));
            }

            case "WARN_USER" -> {
                auditLogger.log(
                        AuditCategory.ADMIN_USERS,
                        AuditAction.REPORT_ACTION_TAKEN,
                        adminId,
                        event.getCreatedByUserId(),
                        event.getId(),
                        Map.of("warning", true, "reason", req.reason));
            }

            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid action. Use: BLOCK_EVENT, HIDE_EVENT, WARN_USER");
        }

        // update report state
        report.setStatus(EventReport.Status.ACTION_TAKEN);
        report.setAdminAction(req.action);
        report.setActionReason(req.reason);
        report.setReviewedBy(adminId);
        report.setReviewedAt(Instant.now());
        reportService.save(report);

        return Map.of(
                "message", "Action applied successfully",
                "eventStatus", event.getStatus().name());
    }

    // ============================================================
    // 7️⃣ ASSIGN MODERATOR
    // ============================================================
    @PostMapping("/{reportId}/assign")
    public Map<String, Object> assign(
            @PathVariable String reportId,
            @RequestParam String moderatorId,
            @RequestHeader("admin-id") String adminHeader) {

        EventReport report = reportService.getReport(reportId);
        String adminId = getAdminId(adminHeader);

        report.setAssignedTo(moderatorId);
        reportService.save(report);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.ADMIN_ASSIGNED_REPORT,
                adminId,
                report.getReporterUserId(),
                report.getEventId(),
                Map.of("assignedTo", moderatorId));

        return Map.of("assignedTo", moderatorId);
    }

    // ============================================================
    // 8️⃣ RESTORE EVENT (undo moderation)
    // ============================================================
    @PostMapping("/events/{eventId}/restore")
    public Map<String, Object> restoreEvent(
            @PathVariable String eventId,
            @RequestParam String adminId) {

        Event event = eventRepo.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        event.setStatus(Event.Status.ACTIVE);
        eventRepo.save(event);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.ADMIN_RESTORED_EVENT,
                adminId,
                event.getCreatedByUserId(),
                eventId,
                Map.of("restored", true));

        return Map.of("message", "Event restored to ACTIVE");
    }

    // ============================================================
    // 9️⃣ REPORT CATEGORIES (UI helper)
    // ============================================================
    @GetMapping("/categories")
    public List<String> categories() {
        return List.of("spam", "abuse", "misleading", "illegal", "fake", "unsafe");
    }

    // ============================================================
    // 🔟 QUEUE STATS
    // ============================================================
    @GetMapping("/stats")
    public Map<String, Long> queueStats() {

        long total = reportService.searchReports(null, null, PageRequest.of(0, 1)).getTotalElements();
        long pending = reportService.searchReports("PENDING", null, PageRequest.of(0, 1)).getTotalElements();
        long approved = reportService.searchReports("APPROVED", null, PageRequest.of(0, 1)).getTotalElements();
        long rejected = reportService.searchReports("REJECTED", null, PageRequest.of(0, 1)).getTotalElements();

        return Map.of(
                "total", total,
                "pending", pending,
                "approved", approved,
                "rejected", rejected);
    }
}
