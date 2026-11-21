package com.planbana.backend.admin.events;

import com.planbana.backend.audit.*;
import com.planbana.backend.chat.ChatRoomService;
import com.planbana.backend.events.Event;
import com.planbana.backend.events.EventLifecycleService;
import com.planbana.backend.events.EventRepository;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

@Service
public class AdminEventService {

    private final EventRepository eventRepo;
    private final ChatRoomService chatRoomService;
    private final AuditLogger auditLogger;
    private final EventLifecycleService lifecycleService;

    public AdminEventService(EventRepository eventRepo, ChatRoomService chatRoomService, AuditLogger auditLogger,
            EventLifecycleService lifecycleService) {
        this.eventRepo = eventRepo;
        this.chatRoomService = chatRoomService;
        this.auditLogger = auditLogger;
        this.lifecycleService = lifecycleService;
    }

    // ==========================================================
    // LIST EVENTS (OPTIONAL STATUS FILTER)
    // ==========================================================
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

    // ==========================================================
    // GET SINGLE EVENT
    // ==========================================================
    public Event getEvent(String id) {
        return eventRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found"));
    }

    // ==========================================================
    // UPDATE STATUS (BLOCK, UNBLOCK, HIDE, REVIEW, ETC.)
    // ==========================================================
    public Event updateStatus(String id, Event.Status newStatus, String adminUserId) {

        Event event = getEvent(id);
        Event.Status oldStatus = event.getStatus();

        event.setStatus(newStatus);
        Event saved = eventRepo.save(event);

        // ---- Choose appropriate admin action ----
        AuditAction auditAction = AuditAction.ADMIN_UPDATED_EVENT_STATUS;

        // ---- Log admin-side action ----
        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                auditAction,
                adminUserId,
                event.getCreatedByUserId(),
                saved.getId(),
                Map.of(
                        "oldStatus", oldStatus != null ? oldStatus.name() : null,
                        "newStatus", newStatus != null ? newStatus.name() : null));

        return saved;
    }

    // ==========================================================
    // ADMIN FORCE CANCEL EVENT
    // ==========================================================
    public Event cancelEvent(String id, String adminUserId) {

        Event event = getEvent(id);

        if (event.isCanceled()) {
            return event;
        }

        event.setCanceled(true);
        Event saved = eventRepo.save(event);

        // Identical fan-out to the host path — a force-cancel is a cancel, and
        // members must be told (broadcast + notification + chat note).
        lifecycleService.onCancelled(
                saved, adminUserId, EventLifecycleService.Initiator.MODERATION);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.ADMIN_BLOCKED_EVENT,
                adminUserId,
                event.getCreatedByUserId(),
                saved.getId(),
                Map.of(
                        "previousCanceledState", false,
                        "newCanceledState", true));

        return saved;
    }

    // ==========================================================
    // HARD DELETE EVENT (DANGEROUS)
    // ==========================================================
    public void deleteEventHard(String id, String adminUserId) {

        Event event = getEvent(id);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.ADMIN_DELETED_EVENT,
                adminUserId,
                event.getCreatedByUserId(),
                event.getId(),
                Map.of(
                        "eventId", event.getId(),
                        "title", event.getTitle(),
                        "createdBy", event.getCreatedByUserId()));

        chatRoomService.deleteEventGroup(event.getId());
        eventRepo.deleteById(id);
    }

}
