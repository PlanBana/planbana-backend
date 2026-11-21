package com.planbana.backend.notification;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Persists a notification for a recipient and pushes a live copy to their
 * per-user STOMP topic {@code /topic/notifications/{recipientUserId}}.
 */
@Service
public class NotificationService {

    /** Each session subscribes clients to /topic/notifications/{recipientUserId}. */
    private static final String TOPIC_PREFIX = "/topic/notifications/";

    private final NotificationRepository repo;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(NotificationRepository repo, SimpMessagingTemplate messagingTemplate) {
        this.repo = repo;
        this.messagingTemplate = messagingTemplate;
    }

    public Notification notify(
            String recipientUserId,
            String type,
            String eventId,
            String actorUserId,
            String title,
            String body) {
        if (recipientUserId == null || recipientUserId.isBlank()) {
            return null;
        }

        Notification n = new Notification();
        n.setRecipientUserId(recipientUserId);
        n.setType(type);
        n.setEventId(eventId);
        n.setActorUserId(actorUserId);
        n.setTitle(title);
        n.setBody(body);
        n.setRead(false);

        try {
            n = repo.save(n);
        } catch (Exception e) {
            // Best-effort persistence; still attempt the live push.
            java.util.logging.Logger.getLogger(NotificationService.class.getName())
                    .warning("Failed to persist notification: " + e.getMessage());
        }

        // Live push (best-effort).
        try {
            messagingTemplate.convertAndSend(TOPIC_PREFIX + recipientUserId, n);
        } catch (Exception e) {
            // ignore — no live subscriber, persisted copy is the source of truth
        }

        return n;
    }
}