// package com.planbana.backend.admin.events;

// import com.planbana.backend.events.Event;
// import com.planbana.backend.events.EventRepository;
// import com.planbana.backend.audit.AuditLogger;
// import com.planbana.backend.audit.AuditAction;
// import com.planbana.backend.audit.AuditCategory;
// import org.springframework.http.HttpStatus;
// import org.springframework.stereotype.Service;
// import org.springframework.web.server.ResponseStatusException;

// @Service
// public class AdminEventActionService {

// private final EventRepository eventRepo;
// private final AuditLogger audit;

// public AdminEventActionService(EventRepository eventRepo, AuditLogger audit)
// {
// this.eventRepo = eventRepo;
// this.audit = audit;
// }

// private Event get(String id) {
// return eventRepo.findById(id)
// .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event
// not found"));
// }

// // -----------------------------
// // 1. Force Cancel Event
// // -----------------------------
// public Event forceCancel(String id, String adminId) {
// Event e = get(id);
// e.setCanceled(true);
// eventRepo.save(e);

// audit.log(
// AuditCategory.ADMIN_EVENTS,
// AuditAction.ADMIN_CANCELED_EVENT,
// adminId,
// null,
// id);

// return e;
// }

// // -----------------------------
// // 2. Block Event
// // -----------------------------
// public Event blockEvent(String id, String adminId) {
// Event e = get(id);
// e.setStatus(Event.Status.BLOCKED);
// eventRepo.save(e);

// audit.log(
// AuditCategory.ADMIN_EVENTS,
// AuditAction.ADMIN_BLOCKED_EVENT,
// adminId,
// null,
// id);

// return e;
// }

// // -----------------------------
// // 3. Unblock Event
// // -----------------------------
// public Event unblockEvent(String id, String adminId) {
// Event e = get(id);
// e.setStatus(Event.Status.ACTIVE);
// eventRepo.save(e);

// audit.log(
// AuditCategory.ADMIN_EVENTS,
// AuditAction.ADMIN_UNBLOCKED_EVENT,
// adminId,
// null,
// id);

// return e;
// }

// // -----------------------------
// // 4. Hide Event
// // -----------------------------
// public Event hideEvent(String id, String adminId) {
// Event e = get(id);
// e.setStatus(Event.Status.HIDDEN);
// eventRepo.save(e);

// audit.log(
// AuditCategory.ADMIN_EVENTS,
// AuditAction.ADMIN_HIDDEN_EVENT,
// adminId,
// null,
// id);

// return e;
// }

// // -----------------------------
// // 5. Mark Pending Review
// // -----------------------------
// public Event markPendingReview(String id, String adminId) {
// Event e = get(id);
// e.setStatus(Event.Status.PENDING_REVIEW);
// eventRepo.save(e);

// audit.log(
// AuditCategory.ADMIN_EVENTS,
// AuditAction.ADMIN_REVIEWED_EVENT_REPORT,
// adminId,
// null,
// id);

// return e;
// }

// // -----------------------------
// // 6. Hard Delete
// // -----------------------------
// public void deleteHard(String id, String adminId) {
// get(id);
// eventRepo.deleteById(id);

// audit.log(
// AuditCategory.ADMIN_EVENTS,
// AuditAction.ADMIN_DELETED_EVENT,
// adminId,
// null,
// id);
// }
// }
