package com.planbana.backend.admin.events;

import com.planbana.backend.events.Event;
import com.planbana.backend.events.EventRepository;
import com.planbana.backend.events.audit.AuditLog;
import com.planbana.backend.events.audit.AuditLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminEventActionService {

    private final EventRepository eventRepo;
    private final AuditLogRepository auditRepo;

    public AdminEventActionService(EventRepository eventRepo, AuditLogRepository auditRepo) {
        this.eventRepo = eventRepo;
        this.auditRepo = auditRepo;
    }

    private Event get(String id) {
        return eventRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }

    private void log(String eventId, String action, String adminId) {
        auditRepo.save(new AuditLog(eventId, action, adminId, null));
    }

    // ============================================================
    // 1. Force Cancel Event
    // ============================================================

    public Event forceCancel(String id, String adminId) {
        Event e = get(id);
        e.setCanceled(true);
        eventRepo.save(e);

        log(id, "FORCE_CANCELED", adminId);
        return e;
    }

    // ============================================================
    // 2. Block Event
    // ============================================================

    public Event blockEvent(String id, String adminId) {
        Event e = get(id);
        e.setStatus(Event.Status.BLOCKED);
        eventRepo.save(e);

        log(id, "EVENT_BLOCKED", adminId);
        return e;
    }

    // ============================================================
    // 3. Unblock Event
    // ============================================================

    public Event unblockEvent(String id, String adminId) {
        Event e = get(id);
        e.setStatus(Event.Status.ACTIVE);
        eventRepo.save(e);

        log(id, "EVENT_UNBLOCKED", adminId);
        return e;
    }

    // ============================================================
    // 4. Hide Event
    // ============================================================

    public Event hideEvent(String id, String adminId) {
        Event e = get(id);
        e.setStatus(Event.Status.HIDDEN);
        eventRepo.save(e);

        log(id, "EVENT_HIDDEN", adminId);
        return e;
    }

    // ============================================================
    // 5. Mark Pending Review
    // ============================================================

    public Event markPendingReview(String id, String adminId) {
        Event e = get(id);
        e.setStatus(Event.Status.PENDING_REVIEW);
        eventRepo.save(e);

        log(id, "EVENT_MARKED_PENDING_REVIEW", adminId);
        return e;
    }

    // ============================================================
    // 6. Hard Delete
    // ============================================================

    public void deleteHard(String id, String adminId) {
        get(id); // ensure exists
        eventRepo.deleteById(id);

        log(id, "EVENT_HARD_DELETED", adminId);
    }
}
