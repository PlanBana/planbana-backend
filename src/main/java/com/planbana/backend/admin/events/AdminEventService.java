package com.planbana.backend.admin.events;

import com.planbana.backend.events.Event;
import com.planbana.backend.events.EventRepository;
import com.planbana.backend.events.audit.AuditLog;
import com.planbana.backend.events.audit.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

@Service
public class AdminEventService {

    private final EventRepository eventRepo;
    private final AuditLogRepository auditRepo;

    public AdminEventService(EventRepository eventRepo, AuditLogRepository auditRepo) {
        this.eventRepo = eventRepo;
        this.auditRepo = auditRepo;
    }

    // ==============================================================
    // Event Listing
    // ==============================================================

    public Page<Event> listByStatus(Event.Status status, int page, int size) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        if (status != null) {
            return eventRepo.findByStatus(status, pageable);
        }

        return eventRepo.findAll(pageable);
    }

    // ==============================================================
    // Get Single Event
    // ==============================================================

    public Event getEvent(String id) {
        return eventRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found"));
    }

    // ==============================================================
    // Update Event Status (Admin Moderation)
    // ==============================================================

    public Event updateStatus(String id, Event.Status newStatus, String adminUserId) {

        Event event = getEvent(id);
        Event.Status oldStatus = event.getStatus();

        event.setStatus(newStatus);
        Event saved = eventRepo.save(event);

        // ============================
        // AUDIT LOG
        // ============================
        AuditLog log = new AuditLog(
                saved.getId(),
                "ADMIN.EVENT_STATUS_UPDATED", // machine friendly
                adminUserId, // admin performing change
                event.getCreatedByUserId(), // event owner
                Instant.now());

        log.setCategory("ADMIN_EVENTS");
        log.setMeta(Map.of(
                "eventId", saved.getId(),
                "oldStatus", oldStatus != null ? oldStatus.name() : null,
                "newStatus", newStatus != null ? newStatus.name() : null));

        auditRepo.save(log);

        return saved;
    }

    // ==============================================================
    // Cancel Event (Admin Force Cancel)
    // ==============================================================

    public Event cancelEvent(String id, String adminUserId) {
        Event event = getEvent(id);

        if (!event.isCanceled()) {
            event.setCanceled(true);
            Event saved = eventRepo.save(event);

            AuditLog log = new AuditLog(
                    saved.getId(),
                    "ADMIN.EVENT_FORCE_CANCELED",
                    adminUserId,
                    event.getCreatedByUserId(),
                    Instant.now());

            log.setCategory("ADMIN_EVENTS");
            log.setMeta(Map.of(
                    "eventId", saved.getId(),
                    "previousCanceledState", false,
                    "newCanceledState", true));

            auditRepo.save(log);

            return saved;
        }

        return event;
    }

    // ==============================================================
    // Hard Delete (Dangerous – irreversible)
    // ==============================================================

    public void deleteEventHard(String id, String adminUserId) {

        Event event = getEvent(id);

        AuditLog log = new AuditLog(
                event.getId(),
                "ADMIN.EVENT_HARD_DELETED",
                adminUserId,
                event.getCreatedByUserId(),
                Instant.now());

        log.setCategory("ADMIN_EVENTS");
        log.setMeta(Map.of(
                "eventId", event.getId(),
                "title", event.getTitle(),
                "createdBy", event.getCreatedByUserId()));

        auditRepo.save(log);

        eventRepo.deleteById(id);
    }
}
