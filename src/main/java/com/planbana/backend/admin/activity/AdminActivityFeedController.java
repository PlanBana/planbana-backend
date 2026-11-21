package com.planbana.backend.admin.activity;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Admin Activity Feed (SSE)
 *
 * Frontend:
 * const evtSource = new EventSource("/api/admin/activity/stream");
 * evtSource.addEventListener("admin-activity", (event) => {
 * const data = JSON.parse(event.data);
 * console.log("Admin activity:", data);
 * });
 */
@RestController
@RequestMapping("/api/admin/activity")
public class AdminActivityFeedController {

    private final AdminActivityFeedService feedService;

    public AdminActivityFeedController(AdminActivityFeedService feedService) {
        this.feedService = feedService;
    }

    // ============================================================
    // 1️⃣ SSE STREAM ENDPOINT
    // ============================================================

    @GetMapping("/stream")
    public SseEmitter stream() {
        return feedService.subscribe();
    }

    // ============================================================
    // 2️⃣ TEST PUBLISH ENDPOINT (optional, useful for debugging)
    // ============================================================

    /**
     * Simple test endpoint to push a manual activity event.
     * You can remove this in production if you want.
     */
    @PostMapping("/test")
    public Map<String, String> sendTestEvent(@RequestBody Map<String, Object> body) {

        feedService.publish(Map.of(
                "type", "TEST_EVENT",
                "payload", body));

        return Map.of("status", "sent");
    }
}
