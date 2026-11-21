// package com.planbana.backend.admin.events;

// import com.planbana.backend.events.Event;
// import org.springframework.security.core.Authentication;
// import org.springframework.web.bind.annotation.*;

// @RestController
// @RequestMapping("/api/admin/events/actions")
// public class AdminEventActionController {

// private final AdminEventActionService service;

// public AdminEventActionController(AdminEventActionService service) {
// this.service = service;
// }

// private String adminId(Authentication auth) {
// return auth.getName(); // phone-based authentication
// }

// // ============================================================
// // 1. Force Cancel
// // ============================================================

// @PostMapping("/{id}/force-cancel")
// public Event forceCancel(@PathVariable String id, Authentication auth) {
// return service.forceCancel(id, adminId(auth));
// }

// // ============================================================
// // 2. Block / Unblock
// // ============================================================

// @PostMapping("/{id}/block")
// public Event block(@PathVariable String id, Authentication auth) {
// return service.blockEvent(id, adminId(auth));
// }

// @PostMapping("/{id}/unblock")
// public Event unblock(@PathVariable String id, Authentication auth) {
// return service.unblockEvent(id, adminId(auth));
// }

// // ============================================================
// // 3. Hide
// // ============================================================

// @PostMapping("/{id}/hide")
// public Event hide(@PathVariable String id, Authentication auth) {
// return service.hideEvent(id, adminId(auth));
// }

// // ============================================================
// // 4. Pending Review
// // ============================================================

// @PostMapping("/{id}/pending-review")
// public Event markPendingReview(@PathVariable String id, Authentication auth)
// {
// return service.markPendingReview(id, adminId(auth));
// }

// // ============================================================
// // 5. Hard Delete
// // ============================================================

// @DeleteMapping("/{id}")
// public void deleteHard(@PathVariable String id, Authentication auth) {
// service.deleteHard(id, adminId(auth));
// }
// }
