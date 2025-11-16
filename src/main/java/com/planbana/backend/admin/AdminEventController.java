package com.planbana.backend.admin;

import com.planbana.backend.events.Event;
import com.planbana.backend.events.EventRepository;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/events")
public class AdminEventController {

    private final EventRepository events;

    public AdminEventController(EventRepository events) {
        this.events = events;
    }

    public record EventSummaryDto(
            String id,
            String title,
            String hostId,
            String status,
            String startAt) {
    }

    public record StatusUpdateRequest(
            String status // e.g. ACTIVE, BLOCKED, HIDDEN, PENDING_REVIEW
    ) {
    }

    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @GetMapping
    public Map<String, Object> listEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Event> result;

        if (status != null && !status.isBlank()) {
            Event.Status enumStatus = Event.Status.valueOf(status.toUpperCase());
            result = events.findByStatus(enumStatus, pageable);
        } else {
            result = events.findAll(pageable);
        }

        return Map.of(
                "content", result.getContent().stream()
                        .map(e -> new EventSummaryDto(
                                e.getId(),
                                e.getTitle(),
                                e.getCreatedByUserId(), // FIXED
                                e.getStatus().name(), // WORKS NOW
                                e.getStartAt().toString()))
                        .toList(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages());
    }

    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @PatchMapping("/{id}/status")
    public Map<String, Object> updateStatus(@PathVariable String id,
            @RequestBody StatusUpdateRequest req) {
        Event e = events.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

        try {
            e.setStatus(Event.Status.valueOf(req.status().toUpperCase()));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
        }

        events.save(e);
        return Map.of(
                "id", e.getId(),
                "status", e.getStatus().name());
    }

}
