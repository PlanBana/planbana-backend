package com.planbana.backend.moderation;

import com.planbana.backend.audit.*;
import com.planbana.backend.events.Event;
import com.planbana.backend.events.EventRepository;
import com.planbana.backend.user.UserRepository;

import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportsController {

    private final EventReportRepository reportRepo;
    private final EventRepository eventRepo;
    private final UserRepository userRepo;
    private final AuditLogger auditLogger;

    public AdminReportsController(
            EventReportRepository reportRepo,
            EventRepository eventRepo,
            UserRepository userRepo,
            AuditLogger auditLogger) {

        this.reportRepo = reportRepo;
        this.eventRepo = eventRepo;
        this.userRepo = userRepo;
        this.auditLogger = auditLogger;
    }

    // ============================================
    // 1️⃣ List all reports (paged)
    // ============================================

    @GetMapping
    public Page<EventReport> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return reportRepo.findAll(pageable);
    }

    // ============================================
    // 2️⃣ Get all reports for one event
    // ============================================

    @GetMapping("/event/{eventId}")
    public List<EventReport> getEventReports(@PathVariable String eventId) {
        return reportRepo.findByEventIdOrderByCreatedAtDesc(eventId);
    }

    // ============================================
    // 3️⃣ Resolve / Dismiss Report
    // ============================================

    @PostMapping("/{reportId}/resolve")
    public Map<String, Object> resolveReport(
            @PathVariable String reportId,
            @RequestParam String adminId) {

        Optional<EventReport> report = reportRepo.findById(reportId);
        if (report.isEmpty()) {
            return Map.of("status", "NOT_FOUND");
        }

        reportRepo.deleteById(reportId);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.REPORT_ACTION_TAKEN,
                adminId,
                null,
                report.get().getEventId(),
                Map.of("resolved", true));

        return Map.of("status", "RESOLVED");
    }

    @PostMapping("/{reportId}/dismiss")
    public Map<String, Object> dismissReport(
            @PathVariable String reportId,
            @RequestParam String adminId) {

        Optional<EventReport> report = reportRepo.findById(reportId);
        if (report.isEmpty()) {
            return Map.of("status", "NOT_FOUND");
        }

        reportRepo.deleteById(reportId);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.REPORT_REVIEWED_BY_ADMIN,
                adminId,
                null,
                report.get().getEventId(),
                Map.of("dismissed", true));

        return Map.of("status", "DISMISSED");
    }
}
