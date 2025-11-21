package com.planbana.backend.admin.events;

import com.planbana.backend.admin.activity.AdminActivityFeedService;
import com.planbana.backend.audit.AuditAction;
import com.planbana.backend.audit.AuditCategory;
import com.planbana.backend.audit.AuditLogger;
import com.planbana.backend.events.Event;
import com.planbana.backend.events.EventRepository;
import com.planbana.backend.moderation.EventReport;
import com.planbana.backend.moderation.EventReportService;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.*;

/**
 * FINAL MERGED VERSION:
 * ------------------------------------------------------
 * Supports:
 * ✔ Block / Unblock
 * ✔ Hide / Unhide
 * ✔ Force Cancel
 * ✔ Force End
 * ✔ Hard Delete
 * ✔ Assign Moderator
 * ✔ Admin Notes
 * ✔ View Reports
 * ✔ Review Reports (APPROVE / REJECT)
 *
 * Uses AuditLogger + AdminActivityFeedService for all admin actions.
 */
@RestController
@RequestMapping("/api/admin/events/moderation")
public class AdminEventModerationController {

    private final EventRepository eventRepo;
    private final UserRepository userRepo;
    private final EventReportService reportService;
    private final AuditLogger auditLogger;
    private final AdminActivityFeedService activityFeed;

    public AdminEventModerationController(
            EventRepository eventRepo,
            UserRepository userRepo,
            EventReportService reportService,
            AuditLogger auditLogger,
            AdminActivityFeedService activityFeed) {
        this.eventRepo = eventRepo;
        this.userRepo = userRepo;
        this.reportService = reportService;
        this.auditLogger = auditLogger;
        this.activityFeed = activityFeed;
    }

    // ============================================================
    // Helper: Resolve admin ID
    // ============================================================
    private String getAdminId(Authentication auth) {
        if (auth == null || auth.getName() == null)
            return "UNKNOWN_ADMIN";

        return userRepo.findByPhone(auth.getName())
                .or(() -> userRepo.findByEmail(auth.getName()))
                .map(User::getId)
                .orElse("UNKNOWN_ADMIN");
    }

    private Event getEvent(String eventId) {
        return eventRepo.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }

    // ============================================================
    // DTOs
    // ============================================================
    public static class AdminRequest {
        public String reason;
    }

    public static class ReviewRequest {
        @NotBlank
        public String action; // APPROVE / REJECT
        public String reason;
    }

    public static class AssignRequest {
        @NotBlank
        public String moderatorId;
    }

    public static class NoteRequest {
        @NotBlank
        public String note;
    }

    // ============================================================
    // 1️⃣ BLOCK EVENT
    // ============================================================
    @PostMapping("/{eventId}/block")
    public Map<String, Object> blockEvent(
            @PathVariable String eventId,
            @RequestBody(required = false) AdminRequest req,
            Authentication auth) {

        Event event = getEvent(eventId);
        String admin = getAdminId(auth);

        Event.Status old = event.getStatus();
        event.setStatus(Event.Status.BLOCKED);
        eventRepo.save(event);

        Map<String, Object> meta = Map.of(
                "oldStatus", old,
                "reason", req != null ? req.reason : null);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.ADMIN_BLOCKED_EVENT,
                admin,
                event.getCreatedByUserId(),
                eventId,
                meta);

        // SSE push
        activityFeed.publish(new HashMap<>(Map.of(
                "type", "EVENT_BLOCKED",
                "eventId", eventId,
                "adminId", admin,
                "oldStatus", old != null ? old.name() : null,
                "newStatus", "BLOCKED",
                "reason", req != null ? req.reason : null)));

        return Map.of("status", "BLOCKED");
    }

    // ============================================================
    // 2️⃣ UNBLOCK EVENT
    // ============================================================
    @PostMapping("/{eventId}/unblock")
    public Map<String, Object> unblockEvent(
            @PathVariable String eventId,
            @RequestBody(required = false) AdminRequest req,
            Authentication auth) {

        Event event = getEvent(eventId);
        String admin = getAdminId(auth);

        Event.Status old = event.getStatus();
        event.setStatus(Event.Status.ACTIVE);
        eventRepo.save(event);

        Map<String, Object> meta = Map.of(
                "oldStatus", old,
                "reason", req != null ? req.reason : null);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.ADMIN_UNBLOCKED_EVENT,
                admin,
                event.getCreatedByUserId(),
                eventId,
                meta);

        activityFeed.publish(new HashMap<>(Map.of(
                "type", "EVENT_UNBLOCKED",
                "eventId", eventId,
                "adminId", admin,
                "oldStatus", old != null ? old.name() : null,
                "newStatus", "ACTIVE",
                "reason", req != null ? req.reason : null)));

        return Map.of("status", "ACTIVE");
    }

    // ============================================================
    // 3️⃣ HIDE EVENT
    // ============================================================
    @PostMapping("/{eventId}/hide")
    public Map<String, Object> hideEvent(
            @PathVariable String eventId,
            @RequestBody(required = false) AdminRequest req,
            Authentication auth) {

        Event event = getEvent(eventId);
        String admin = getAdminId(auth);

        Event.Status old = event.getStatus();
        event.setStatus(Event.Status.HIDDEN);
        eventRepo.save(event);

        Map<String, Object> meta = Map.of(
                "oldStatus", old,
                "reason", req != null ? req.reason : null);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.ADMIN_HIDDEN_EVENT,
                admin,
                event.getCreatedByUserId(),
                eventId,
                meta);

        activityFeed.publish(new HashMap<>(Map.of(
                "type", "EVENT_HIDDEN",
                "eventId", eventId,
                "adminId", admin,
                "oldStatus", old != null ? old.name() : null,
                "newStatus", "HIDDEN",
                "reason", req != null ? req.reason : null)));

        return Map.of("status", "HIDDEN");
    }

    // ============================================================
    // 4️⃣ UNHIDE EVENT
    // ============================================================
    @PostMapping("/{eventId}/unhide")
    public Map<String, Object> unhideEvent(
            @PathVariable String eventId,
            @RequestBody(required = false) AdminRequest req,
            Authentication auth) {

        Event event = getEvent(eventId);
        String admin = getAdminId(auth);

        Event.Status old = event.getStatus();
        event.setStatus(Event.Status.ACTIVE);
        eventRepo.save(event);

        Map<String, Object> meta = Map.of(
                "oldStatus", old,
                "reason", req != null ? req.reason : null);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.ADMIN_UNHIDDEN_EVENT,
                admin,
                event.getCreatedByUserId(),
                eventId,
                meta);

        activityFeed.publish(new HashMap<>(Map.of(
                "type", "EVENT_UNHIDDEN",
                "eventId", eventId,
                "adminId", admin,
                "oldStatus", old != null ? old.name() : null,
                "newStatus", "ACTIVE",
                "reason", req != null ? req.reason : null)));

        return Map.of("status", "ACTIVE");
    }

    // ============================================================
    // 5️⃣ FORCE CANCEL EVENT
    // ============================================================
    @PostMapping("/{eventId}/cancel")
    public Map<String, Object> cancelEvent(
            @PathVariable String eventId,
            @RequestBody(required = false) AdminRequest req,
            Authentication auth) {

        Event event = getEvent(eventId);
        String admin = getAdminId(auth);

        boolean oldCanceled = event.isCanceled();
        event.setCanceled(true);
        eventRepo.save(event);

        Map<String, Object> meta = Map.of(
                "oldCanceled", oldCanceled,
                "reason", req != null ? req.reason : null);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.ADMIN_CANCELED_EVENT,
                admin,
                event.getCreatedByUserId(),
                eventId,
                meta);

        activityFeed.publish(new HashMap<>(Map.of(
                "type", "EVENT_CANCELED",
                "eventId", eventId,
                "adminId", admin,
                "oldCanceled", oldCanceled,
                "newCanceled", true,
                "reason", req != null ? req.reason : null)));

        return Map.of("status", "CANCELED");
    }

    // ============================================================
    // 6️⃣ FORCE END EVENT
    // ============================================================
    @PostMapping("/{eventId}/end")
    public Map<String, Object> endEvent(
            @PathVariable String eventId,
            Authentication auth) {

        Event event = getEvent(eventId);
        String admin = getAdminId(auth);

        event.setEnded(true);
        event.setEndedAt(Instant.now());
        eventRepo.save(event);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.ADMIN_ENDED_EVENT,
                admin,
                event.getCreatedByUserId(),
                eventId,
                Map.of("message", "Event forcibly ended"));

        activityFeed.publish(new HashMap<>(Map.of(
                "type", "EVENT_ENDED",
                "eventId", eventId,
                "adminId", admin)));

        return Map.of("status", "ENDED");
    }

    // ============================================================
    // 7️⃣ HARD DELETE EVENT
    // ============================================================
    @DeleteMapping("/{eventId}")
    public Map<String, Object> deleteEvent(
            @PathVariable String eventId,
            Authentication auth) {

        Event event = getEvent(eventId);
        String admin = getAdminId(auth);

        String title = event.getTitle();
        String ownerId = event.getCreatedByUserId();

        eventRepo.delete(event);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.ADMIN_DELETED_EVENT,
                admin,
                ownerId,
                eventId,
                Map.of("title", title));

        activityFeed.publish(new HashMap<>(Map.of(
                "type", "EVENT_DELETED",
                "eventId", eventId,
                "adminId", admin,
                "title", title,
                "ownerId", ownerId)));

        return Map.of("deleted", true);
    }

    // ============================================================
    // 8️⃣ EVENT NOTES (Admin)
    // ============================================================
    @PostMapping("/{eventId}/notes")
    public Map<String, Object> addNote(
            @PathVariable String eventId,
            @RequestBody NoteRequest req,
            Authentication auth) {

        Event event = getEvent(eventId);
        String admin = getAdminId(auth);

        Event.AdminNote note = new Event.AdminNote(admin, req.note);
        event.getAdminNotes().add(note);
        eventRepo.save(event);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.ADMIN_EVENT_NOTE_ADDED,
                admin,
                event.getCreatedByUserId(),
                eventId,
                Map.of("note", req.note));

        activityFeed.publish(new HashMap<>(Map.of(
                "type", "EVENT_NOTE_ADDED",
                "eventId", eventId,
                "adminId", admin,
                "noteId", note.getId(),
                "note", req.note)));

        return Map.of("noteId", note.getId(), "createdAt", note.getCreatedAt());
    }

    @GetMapping("/{eventId}/notes")
    public List<Event.AdminNote> listNotes(@PathVariable String eventId) {
        return getEvent(eventId).getAdminNotes();
    }

    // ============================================================
    // 9️⃣ Assign To Moderator
    // ============================================================
    @PostMapping("/{eventId}/assign")
    public Map<String, Object> assignEvent(
            @PathVariable String eventId,
            @RequestBody AssignRequest req,
            Authentication auth) {

        Event event = getEvent(eventId);
        String admin = getAdminId(auth);

        event.setAssignedModerator(req.moderatorId);
        eventRepo.save(event);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.ADMIN_ASSIGNED_EVENT,
                admin,
                event.getCreatedByUserId(),
                eventId,
                Map.of("assignedTo", req.moderatorId));

        activityFeed.publish(new HashMap<>(Map.of(
                "type", "EVENT_ASSIGNED",
                "eventId", eventId,
                "adminId", admin,
                "assignedTo", req.moderatorId)));

        return Map.of("assignedTo", req.moderatorId);
    }

    // ============================================================
    // 🔟 VIEW REPORTS
    // ============================================================
    @GetMapping("/{eventId}/reports")
    public List<EventReport> getReports(@PathVariable String eventId) {
        return reportService.listReportsForEvent(eventId);
    }

    // ============================================================
    // 1️⃣1️⃣ REVIEW REPORT (APPROVE / REJECT)
    // ============================================================
    @PostMapping("/reports/{reportId}/review")
    public Map<String, Object> reviewReport(
            @PathVariable String reportId,
            @RequestBody ReviewRequest req,
            Authentication auth) {

        EventReport report = reportService.getReport(reportId);
        String admin = getAdminId(auth);

        EventReport.Status status;
        if (req.action.equalsIgnoreCase("APPROVE")) {
            status = EventReport.Status.APPROVED;
        } else if (req.action.equalsIgnoreCase("REJECT")) {
            status = EventReport.Status.REJECTED;
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid review action");
        }

        report.setStatus(status);
        report.setActionReason(req.reason);
        report.setReviewedAt(Instant.now());
        report.setReviewedBy(admin);
        reportService.save(report);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.ADMIN_REVIEWED_EVENT_REPORT,
                admin,
                null,
                report.getEventId(),
                Map.of("reportId", reportId, "result", status));

        activityFeed.publish(new HashMap<>(Map.of(
                "type", "EVENT_REPORT_REVIEWED",
                "reportId", reportId,
                "eventId", report.getEventId(),
                "adminId", admin,
                "result", status.toString(),
                "reason", req.reason)));

        return Map.of("status", status.toString());
    }
}
