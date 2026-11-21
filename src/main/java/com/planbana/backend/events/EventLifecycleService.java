package com.planbana.backend.events;

import com.planbana.backend.chat.ChatRoom;
import com.planbana.backend.chat.ChatRoomRepository;
import com.planbana.backend.chat.Message;
import com.planbana.backend.chat.MessageRepository;
import com.planbana.backend.notification.NotificationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for the cancel / restore side effects.
 *
 * <p>
 * A lifecycle flip is never just one write: it has to (1) broadcast on the
 * public topic so every open view flips live, (2) notify every affected
 * member, and (3) leave a durable note in the event chat. Those three steps
 * used to be duplicated in the host path only — the admin force-cancel path
 * silently skipped all of them, so members were never told.
 *
 * <p>
 * Both entry points now funnel through here, so a future third caller cannot
 * drift: {@code onCancelled} / {@code onRestored} is the only way in.
 */
@Service
public class EventLifecycleService {

  private static final Logger logger = LoggerFactory.getLogger(EventLifecycleService.class);

  private final NotificationService notificationService;
  private final ChatRoomRepository chatRoomRepo;
  private final MessageRepository messageRepo;
  private final SimpMessagingTemplate messagingTemplate;

  public EventLifecycleService(
      NotificationService notificationService,
      ChatRoomRepository chatRoomRepo,
      MessageRepository messageRepo,
      SimpMessagingTemplate messagingTemplate) {
    this.notificationService = notificationService;
    this.chatRoomRepo = chatRoomRepo;
    this.messageRepo = messageRepo;
    this.messagingTemplate = messagingTemplate;
  }

  /**
   * Who flipped the state. This is not cosmetic: when the host cancels, the
   * host / co-hosts already know and are excluded from the fan-out, but when
   * moderation cancels, the host is the single most important recipient.
   */
  public enum Initiator {
    HOST,
    MODERATION
  }

  /** Full fan-out for a cancellation: live broadcast + notification + chat note. */
  public void onCancelled(Event e, String actorUserId, Initiator initiator) {
    broadcastEventLifecycle(e.getId(), true);
    notifyAffectedUsers(e, true, actorUserId, initiator);
    postLifecycleChatMessage(e, true, actorUserId, initiator);
  }

  /** Full fan-out for a restore — mirror image of {@link #onCancelled}. */
  public void onRestored(Event e, String actorUserId, Initiator initiator) {
    broadcastEventLifecycle(e.getId(), false);
    notifyAffectedUsers(e, false, actorUserId, initiator);
    postLifecycleChatMessage(e, false, actorUserId, initiator);
  }

  /**
   * Public lifecycle channel for a single event. Cancel/restore flips are
   * broadcast here so every open view (shared-link guests, members, other
   * tabs) updates without a manual refresh. No auth required to subscribe —
   * the payload carries only the id + cancelled flag.
   */
  public void broadcastEventLifecycle(String eventId, boolean cancelled) {
    try {
      messagingTemplate.convertAndSend(
          "/topic/events/" + eventId + "/lifecycle",
          Map.of(
              "type", "EVENT_LIFECYCLE",
              "eventId", eventId,
              "cancelled", cancelled));
    } catch (Exception ex) {
      logger.warn("Failed to broadcast lifecycle for {}: {}", eventId, ex.getMessage());
    }
  }

  /**
   * Everyone affected gets told. Recipients = confirmed participants + PENDING
   * join requests + WAITLISTED users. The actor never notifies herself, and
   * only a host-initiated flip suppresses the host / co-hosts (they performed
   * it); moderation flips include them, because they must know.
   */
  private void notifyAffectedUsers(Event e, boolean cancelled, String actorUserId, Initiator initiator) {
    Set<String> recipients = affectedUserIds(e);

    if (actorUserId != null)
      recipients.remove(actorUserId);

    if (initiator == Initiator.HOST) {
      recipients.remove(e.getCreatedByUserId());
      if (e.getCoHostUserIds() != null)
        recipients.removeAll(e.getCoHostUserIds());
    }

    String type = cancelled ? "EVENT_CANCELLED" : "EVENT_RESTORED";
    String title = cancelled ? "Event cancelled" : "Event is back on";
    String by = initiator == Initiator.MODERATION ? "Planbana moderation" : "the host";
    String body = cancelled
        ? "“" + e.getTitle() + "” was cancelled by " + by
            + ". Your spot is released — details / refunds will be shared in the event chat."
        : "“" + e.getTitle() + "” was restored by " + by + " and is accepting members again.";
    // A moderation actor id is an admin account, not a member — linking it
    // would make the bell render an unresolved avatar.
    String notificationActor = initiator == Initiator.MODERATION ? null : actorUserId;

    for (String recipientId : recipients) {
      try {
        notificationService.notify(recipientId, type, e.getId(), notificationActor, title, body);
      } catch (Exception ex) {
        logger.warn("Failed to send {} to {}: {}", type, recipientId, ex.getMessage());
      }
    }
  }

  /** Confirmed participants + pending / waitlisted requesters. */
  private Set<String> affectedUserIds(Event e) {
    Set<String> recipients = new HashSet<>();
    if (e.getParticipants() != null)
      recipients.addAll(e.getParticipants());
    if (e.getJoinRequests() != null) {
      for (Event.JoinRequest jr : e.getJoinRequests()) {
        if (jr != null && jr.getUserId() != null
            && (jr.getJoinStatus() == Event.JoinStatus.PENDING
                || jr.getJoinStatus() == Event.JoinStatus.WAITLISTED)) {
          recipients.add(jr.getUserId());
        }
      }
    }
    if (e.getParticipantsWaitingList() != null)
      recipients.addAll(e.getParticipantsWaitingList());
    return recipients;
  }

  /**
   * Server-side lifecycle note in the event chat so the reason survives even
   * if push notifications are delayed. No-op when no chat room exists yet.
   */
  private void postLifecycleChatMessage(Event e, boolean cancelled, String actorUserId, Initiator initiator) {
    try {
      var roomOpt = chatRoomRepo.findByEventId(e.getId());
      if (roomOpt.isEmpty())
        return;

      ChatRoom room = roomOpt.get();
      boolean byModeration = initiator == Initiator.MODERATION;

      Message chatMsg = new Message();
      chatMsg.setChatRoomId(room.getId());
      chatMsg.setSenderId(actorUserId != null ? actorUserId : e.getCreatedByUserId());
      chatMsg.setSenderName(byModeration ? "Planbana Moderation" : "Host");
      if (cancelled) {
        chatMsg.setText(byModeration
            ? "🚫 This event was cancelled by Planbana moderation. Thanks for your interest — next steps will be shared here."
            : "🚫 This event was cancelled by the host. Thanks for your interest — refunds / next plan will be shared here.");
      } else {
        chatMsg.setText(byModeration
            ? "✅ This event was restored by Planbana moderation and is accepting members again."
            : "✅ This event is back on! The host restored it and it is accepting members again.");
      }
      chatMsg.setSentAt(Instant.now());
      messageRepo.save(chatMsg);

      // Live broadcast on the same topic the socket controller uses, so open
      // threads append it without refetch.
      try {
        messagingTemplate.convertAndSend("/topic/chat/" + e.getId(), chatMsg);
      } catch (Exception ignored) {
        // Persisted copy is source of truth — live push is best-effort.
      }
    } catch (Exception ex) {
      logger.warn("Failed to post lifecycle chat message: {}", ex.getMessage());
    }
  }
}
