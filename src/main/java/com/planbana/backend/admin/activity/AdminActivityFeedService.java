package com.planbana.backend.admin.activity;

import com.planbana.backend.audit.AuditLog;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that manages SSE connections for the Admin Activity Feed.
 *
 * Usage:
 * - Controller calls subscribe() to create an SseEmitter per client
 * - Other services can call publish(...) or publishAudit(...) to push events
 */
@Service
public class AdminActivityFeedService {

    // clientId -> emitter
    private final Map<String, SseEmitter> clients = new ConcurrentHashMap<>();

    // Default timeout: 30 minutes
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    /**
     * Subscribe a new admin client to the activity feed.
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String clientId = UUID.randomUUID().toString();

        clients.put(clientId, emitter);

        emitter.onCompletion(() -> clients.remove(clientId));
        emitter.onTimeout(() -> clients.remove(clientId));
        emitter.onError(e -> clients.remove(clientId));

        // Send initial "connected" event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of(
                            "clientId", clientId,
                            "timestamp", Instant.now().toString())));
        } catch (IOException e) {
            clients.remove(clientId);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * Publish a generic admin activity payload to all connected clients.
     * You can call this from anywhere (e.g. Admin controllers).
     */
    public void publish(Map<String, Object> payload) {
        // Add generic timestamp if not present
        payload.putIfAbsent("timestamp", Instant.now().toString());

        clients.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("admin-activity")
                        .data(payload));
            } catch (IOException e) {
                emitter.complete();
                clients.remove(id);
            }
        });
    }

    /**
     * Convenience: publish from an AuditLog entity.
     * Later you can call this from your AuditLogger.
     */
    public void publishAudit(AuditLog log) {
        if (log == null)
            return;

        Map<String, Object> payload = Map.of(
                "id", log.getId(),
                "category", log.getCategory() != null ? log.getCategory().name() : null,
                "action", log.getAction() != null ? log.getAction().name() : null,
                "performedBy", log.getPerformedBy(),
                "targetUserId", log.getTargetUser(),
                "eventId", log.getEventId(),
                "meta", log.getMeta(),
                "timestamp", log.getTimestamp() != null
                        ? log.getTimestamp().toString()
                        : Instant.now().toString());

        publish(payload);
    }
}
