// package com.planbana.backend.admin.events;

// import com.planbana.backend.audit.AuditAction;
// import com.planbana.backend.audit.AuditCategory;
// import com.planbana.backend.audit.AuditLogger;
// import com.planbana.backend.events.Event;
// import org.springframework.data.domain.Page;
// import org.springframework.security.core.Authentication;
// import org.springframework.web.bind.annotation.*;

// import jakarta.validation.constraints.Max;
// import jakarta.validation.constraints.Min;

// import java.time.Instant;
// import java.util.Set;
// import java.util.List;
// import java.util.Map;

// @RestController
// @RequestMapping("/api/admin/events")
// public class AdminEventController {

//     private final AdminEventService service;
//     private final AuditLogger auditLogger;

//     public AdminEventController(AdminEventService service, AuditLogger auditLogger) {
//         this.service = service;
//         this.auditLogger = auditLogger;
//     }

//     // =========================================================
//     // DTOs
//     // =========================================================

//     public static class EventSummary {
//         public String id;
//         public String title;
//         public String description;
//         public Instant startAt;
//         public Instant endAt;

//         public String imageUrl;

//         public String category;
//         public String price;
//         public String pricingType;

//         public Event.Status status;

//         public String createdByUserId;
//         public boolean isCanceled;

//         public long likeCount;

//         public int participantsCount;
//         public int waitingListCount;

//         public Set<String> tags;

//         public static EventSummary from(Event e) {
//             EventSummary s = new EventSummary();
//             s.id = e.getId();
//             s.title = e.getTitle();
//             s.description = e.getDescription();
//             s.startAt = e.getStartAt();
//             s.endAt = e.getEndAt();
//             s.imageUrl = e.getImageUrl();
//             s.category = e.getCategory();
//             s.price = e.getPrice();
//             s.pricingType = e.getPricingType();
//             s.status = e.getStatus();
//             s.createdByUserId = e.getCreatedByUserId();
//             s.isCanceled = e.isCanceled();
//             s.likeCount = e.getLikeCount();
//             s.tags = e.getTags();
//             s.participantsCount = e.getParticipants() != null ? e.getParticipants().size() : 0;
//             s.waitingListCount = e.getParticipantsWaitingList() != null
//                     ? e.getParticipantsWaitingList().size()
//                     : 0;

//             return s;
//         }
//     }

//     public static class EventDetail extends EventSummary {
//         public Integer maxParticipants;
//         public Integer spotsLeft;

//         public String requirements;
//         public String additionalGuidelines;

//         public List<Event.JoinRequest> joinRequests;

//         public Set<String> participants;
//         public Set<String> participantsWaitingList;
//         public Set<String> coHostUserIds;

//         public Object location;
//         public Object startLocation;
//         public Object destinationLocation;

//         public static EventDetail from(Event e) {
//             EventDetail d = new EventDetail();
//             EventSummary base = EventSummary.from(e);

//             d.id = base.id;
//             d.title = base.title;
//             d.description = base.description;
//             d.startAt = base.startAt;
//             d.endAt = base.endAt;
//             d.imageUrl = base.imageUrl;
//             d.category = base.category;
//             d.price = base.price;
//             d.pricingType = base.pricingType;
//             d.status = base.status;
//             d.createdByUserId = base.createdByUserId;
//             d.isCanceled = base.isCanceled;
//             d.likeCount = base.likeCount;
//             d.tags = base.tags;
//             d.participantsCount = base.participantsCount;
//             d.waitingListCount = base.waitingListCount;

//             d.maxParticipants = e.getMaxParticipants();
//             d.spotsLeft = e.getSpotsLeft();
//             d.requirements = e.getRequirements();
//             d.additionalGuidelines = e.getAdditionalGuidelines();

//             d.joinRequests = e.getJoinRequests();
//             d.participants = e.getParticipants();
//             d.participantsWaitingList = e.getParticipantsWaitingList();
//             d.coHostUserIds = e.getCoHostUserIds();

//             d.location = e.getLocation();
//             d.startLocation = e.getStartLocation();
//             d.destinationLocation = e.getDestinationLocation();

//             return d;
//         }
//     }

//     public static class UpdateStatusRequest {
//         public Event.Status status;
//     }

//     // =========================================================
//     // Endpoints
//     // =========================================================

//     @GetMapping
//     public Page<EventSummary> listEvents(
//             @RequestParam(required = false) Event.Status status,
//             @RequestParam(defaultValue = "0") @Min(0) int page,
//             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
//             Authentication auth) {

//         String adminId = auth.getName();

//         // 🔐 Log admin view list
//         auditLogger.log(
//                 AuditCategory.ADMIN_EVENTS,
//                 AuditAction.ADMIN_VIEW_EVENT_LIST,
//                 adminId,
//                 null,
//                 null,
//                 Map.of("statusFilter", status, "page", page, "size", size));

//         Page<Event> p = service.listByStatus(status, page, size);
//         return p.map(EventSummary::from);
//     }

//     @GetMapping("/{id}")
//     public EventDetail getEvent(@PathVariable String id, Authentication auth) {

//         String adminId = auth.getName();
//         Event e = service.getEvent(id);

//         // 🔐 Log admin viewing event detail
//         auditLogger.log(
//                 AuditCategory.ADMIN_EVENTS,
//                 AuditAction.ADMIN_VIEW_EVENT_DETAIL,
//                 adminId,
//                 e.getCreatedByUserId(),
//                 e.getId(),
//                 Map.of("title", e.getTitle(), "status", e.getStatus()));

//         return EventDetail.from(e);
//     }

//     @PatchMapping("/{id}/status")
//     public EventDetail updateStatus(
//             @PathVariable String id,
//             @RequestBody UpdateStatusRequest req,
//             Authentication auth) {

//         String adminId = auth.getName();

//         Event updated = service.updateStatus(id, req.status, adminId);

//         // 🔐 Log admin changing event status
//         auditLogger.log(
//                 AuditCategory.ADMIN_EVENTS,
//                 AuditAction.ADMIN_UPDATED_EVENT_STATUS,
//                 adminId,
//                 updated.getCreatedByUserId(),
//                 updated.getId(),
//                 Map.of("newStatus", req.status));

//         return EventDetail.from(updated);
//     }
// }

package com.planbana.backend.admin.events;

import com.planbana.backend.audit.AuditAction;
import com.planbana.backend.audit.AuditCategory;
import com.planbana.backend.audit.AuditLogger;
import com.planbana.backend.events.Event;

import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/events")
public class AdminEventController {

    private final AdminEventService service;
    private final AuditLogger auditLogger;

    public AdminEventController(AdminEventService service, AuditLogger auditLogger) {
        this.service = service;
        this.auditLogger = auditLogger;
    }

    // =========================================================
    // DTOs
    // =========================================================

    public static class EventSummary {
        public String id;
        public String title;
        public String description;
        public Instant startAt;
        public Instant endAt;
        public String imageUrl;
        public String category;
        public String price;
        public String pricingType;
        public Event.Status status;
        public String createdByUserId;
        public boolean isCanceled;
        public long likeCount;
        public int participantsCount;
        public int waitingListCount;
        public Set<String> tags;

        public static EventSummary from(Event e) {
            EventSummary s = new EventSummary();
            s.id = e.getId();
            s.title = e.getTitle();
            s.description = e.getDescription();
            s.startAt = e.getStartAt();
            s.endAt = e.getEndAt();
            s.imageUrl = e.getImageUrl();
            s.category = e.getCategory();
            s.price = e.getPrice();
            s.pricingType = e.getPricingType();
            s.status = e.getStatus();
            s.createdByUserId = e.getCreatedByUserId();
            s.isCanceled = e.isCanceled();
            s.likeCount = e.getLikeCount();
            s.tags = e.getTags();
            s.participantsCount = e.getParticipants() != null ? e.getParticipants().size() : 0;
            s.waitingListCount = e.getParticipantsWaitingList() != null
                    ? e.getParticipantsWaitingList().size()
                    : 0;
            return s;
        }
    }

    public static class EventDetail extends EventSummary {
        public Integer maxParticipants;
        public Integer spotsLeft;

        public String requirements;
        public String additionalGuidelines;

        public List<Event.JoinRequest> joinRequests;

        public Set<String> participants;
        public Set<String> participantsWaitingList;
        public Set<String> coHostUserIds;

        public Object location;
        public Object startLocation;
        public Object destinationLocation;

        public static EventDetail from(Event e) {
            EventDetail d = new EventDetail();
            EventSummary base = EventSummary.from(e);

            d.id = base.id;
            d.title = base.title;
            d.description = base.description;
            d.startAt = base.startAt;
            d.endAt = base.endAt;
            d.imageUrl = base.imageUrl;
            d.category = base.category;
            d.price = base.price;
            d.pricingType = base.pricingType;
            d.status = base.status;
            d.createdByUserId = base.createdByUserId;
            d.isCanceled = base.isCanceled;
            d.likeCount = base.likeCount;
            d.tags = base.tags;
            d.participantsCount = base.participantsCount;
            d.waitingListCount = base.waitingListCount;

            d.maxParticipants = e.getMaxParticipants();
            d.spotsLeft = e.getSpotsLeft();
            d.requirements = e.getRequirements();
            d.additionalGuidelines = e.getAdditionalGuidelines();

            d.joinRequests = e.getJoinRequests();
            d.participants = e.getParticipants();
            d.participantsWaitingList = e.getParticipantsWaitingList();
            d.coHostUserIds = e.getCoHostUserIds();

            d.location = e.getLocation();
            d.startLocation = e.getStartLocation();
            d.destinationLocation = e.getDestinationLocation();

            return d;
        }
    }

    public static class UpdateStatusRequest {
        public Event.Status status;
    }

    // =========================================================
    // Endpoints
    // =========================================================

    @GetMapping
    public Page<EventSummary> listEvents(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication auth) {

        Event.Status realStatus = null;

        if (status != null && !status.isBlank()) {
            try {
                realStatus = Event.Status.valueOf(status);
            } catch (IllegalArgumentException ignored) {
            }
        }

        String adminId = auth.getName();

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.ADMIN_VIEW_EVENT_LIST,
                adminId,
                null,
                null,
                Map.of("statusFilter", realStatus, "page", page, "size", size));

        Page<Event> p = service.listByStatus(realStatus, page, size);
        return p.map(EventSummary::from);
    }

    @GetMapping("/{id}")
    public EventDetail getEvent(@PathVariable String id, Authentication auth) {

        String adminId = auth.getName();
        Event e = service.getEvent(id);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.ADMIN_VIEW_EVENT_DETAIL,
                adminId,
                e.getCreatedByUserId(),
                e.getId(),
                Map.of("title", e.getTitle(), "status", e.getStatus()));

        return EventDetail.from(e);
    }

    @PatchMapping("/{id}/status")
    public EventDetail updateStatus(
            @PathVariable String id,
            @RequestBody UpdateStatusRequest req,
            Authentication auth) {

        String adminId = auth.getName();

        Event updated = service.updateStatus(id, req.status, adminId);

        auditLogger.log(
                AuditCategory.ADMIN_EVENTS,
                AuditAction.ADMIN_UPDATED_EVENT_STATUS,
                adminId,
                updated.getCreatedByUserId(),
                updated.getId(),
                Map.of("newStatus", req.status));

        return EventDetail.from(updated);
    }
}
