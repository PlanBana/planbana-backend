package com.planbana.backend.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.planbana.backend.admin.settings.MaintenanceGuard;
import com.planbana.backend.events.Event;
import com.planbana.backend.events.EventRepository;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.messaging.Message;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Controller
public class ChatSocketController {

    private final SimpMessagingTemplate template;
    private final MessageRepository messages;
    private final ChatRoomRepository chatRooms;
    private final EventRepository events;
    private final UserRepository users;
    private final MaintenanceGuard maintenanceGuard;
    private final ChatRoomService chatRoomService;

    public ChatSocketController(SimpMessagingTemplate template, MessageRepository messages,
            ChatRoomRepository chatRooms, UserRepository users, MaintenanceGuard maintenanceGuard,
            ChatRoomService chatRoomService, EventRepository events) {
        this.template = template;
        this.messages = messages;
        this.chatRooms = chatRooms;
        this.users = users;
        this.maintenanceGuard = maintenanceGuard;
        this.chatRoomService = chatRoomService;
        this.events = events;
    }

    @MessageMapping("/chat/{eventId}")
    public void sendMessage(@DestinationVariable String eventId, @Payload MessagePayload payload, Message<?> stompMsg) {
        // FIX: Extract auth from Stomp message headers - Authentication param doesn't
        // work reliably with WebSocket
        Authentication auth = extractAuth(stompMsg);

        maintenanceGuard.blockIfMaintenance(auth);
        System.out.println("📥 Received message for event " + eventId);
        System.out.println("📨 Payload: " + payload.getText());
        System.out.println("👤 Auth: " + (auth != null ? auth.getName() : "null"));

        if (auth == null) {
            System.out.println("❌ No authentication, skipping.");
            return;
        }

        var userOpt = users.findByPhone(auth.getName());
        if (userOpt.isEmpty()) {
            System.out.println("❌ User not found: " + auth.getName());
            return;
        }

        User user = userOpt.get();
        String userId = user.getId();

        ChatRoom room = chatRooms.findByEventId(eventId).orElseGet(() -> {
            ChatRoom newRoom = new ChatRoom(eventId, "Chat for event " + eventId, userId);
            newRoom.getParticipants().add(userId);
            return chatRooms.save(newRoom);
        });

        if (!chatRoomService.canInteract(room, user)) {
            System.out.println(
                    "⚠️ User " + user.getPhone() + " (id=" + userId + ") not in participants for chatroom " + eventId);
            return;
        }

        // Option B host-only: once the host cancels, only the host/co-host may
        // post (refund / next-plan updates). Members stay read-only.
        // Frontend already hides the composer — this is the enforced gate.
        var eventOpt = events.findById(eventId);
        if (eventOpt.isPresent() && eventOpt.get().isCanceled()) {
            Event event = eventOpt.get();
            boolean isManager = event.isHost(userId) || event.isCoHost(userId);
            if (!isManager) {
                System.out.println("🚫 Cancelled event " + eventId + " — non-host send blocked for " + userId);
                template.convertAndSendToUser(userId, "/queue/errors",
                        java.util.Map.of("error", "EVENT_CANCELLED_READ_ONLY",
                                "message", "This event was cancelled — only the host can message here."));
                return;
            }
        }

        // Use full class name for our domain Message to avoid conflict with Spring's
        // Message
        com.planbana.backend.chat.Message chatMsg = new com.planbana.backend.chat.Message();
        chatMsg.setChatRoomId(room.getId());
        chatMsg.setSenderId(userId);
        chatMsg.setSenderName(senderName(user));
        chatMsg.setSenderAvatarUrl(senderAvatarUrl(user));
        chatMsg.setText(payload.getText());
        chatMsg.setSentAt(Instant.now());

        // Attachment metadata (empty for plain TEXT messages)
        chatMsg.setMessageType(payload.getMessageType() == null ? "TEXT" : payload.getMessageType());
        chatMsg.setAttachmentUrl(payload.getAttachmentUrl());
        chatMsg.setAttachmentName(payload.getAttachmentName());
        chatMsg.setAttachmentMimeType(payload.getAttachmentMimeType());
        chatMsg.setAttachmentSize(payload.getAttachmentSize());
        chatMsg.setAttachmentStorageKey(payload.getAttachmentStorageKey());

        // Reply-to: persist only the referenced id; populate the quoted text and
        // sender (transient, derived from the original message) for the broadcast.
        // The original message is NEVER modified here — it stays a separate record.
        if (payload.getReplyToMessageId() != null && !payload.getReplyToMessageId().isBlank()) {
            messages.findById(payload.getReplyToMessageId()).ifPresent(original -> {
                chatMsg.setReplyToMessageId(original.getId());
                chatMsg.setReplyToText(original.getText());
                users.findById(original.getSenderId())
                        .map(this::senderName)
                        .ifPresent(name -> chatMsg.setReplyToSenderName(name));
            });
        }

        messages.save(chatMsg);

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            System.out.println("📦 Sending message: " + mapper.writeValueAsString(chatMsg));
        } catch (Exception e) {
            System.err.println("⚠️ Failed to serialize message:");
            e.printStackTrace();
        }

        template.convertAndSend("/topic/chat/" + eventId, chatMsg);
        System.out.println("💬 Message from " + chatMsg.getSenderName() + " sent to /topic/chat/" + eventId);
    }

    /**
     * Typing indicator — transient presence, never persisted. Validates auth &
     * membership, then relays {@code {event, senderId, senderName, typing}} to
     * subscribers of {@code /topic/chat/typing/{eventId}}.
     */
    @MessageMapping("/chat/typing/{eventId}")
    public void typing(@DestinationVariable String eventId, @Payload TypingPayload payload, Message<?> stompMsg) {
        Authentication auth = extractAuth(stompMsg);
        if (auth == null) {
            return;
        }
        var userOpt = users.findByPhone(auth.getName());
        if (userOpt.isEmpty()) {
            return;
        }
        User user = userOpt.get();

        ChatRoom room = chatRooms.findByEventId(eventId).orElse(null);
        if (room == null) {
            return;
        }
        if (!chatRoomService.canInteract(room, user)) {
            return;
        }

        Map<String, Object> presence = new HashMap<>();
        presence.put("event", "TYPING");
        presence.put("eventId", eventId);
        presence.put("senderId", user.getId());
        presence.put("senderName", senderName(user));
        presence.put("typing", payload != null && payload.isTyping());

        template.convertAndSend("/topic/chat/typing/" + eventId, presence);
    }

    private Authentication extractAuth(Message<?> message) {
        // Try Spring Security context first
        Authentication ctxAuth = SecurityContextHolder.getContext().getAuthentication();
        if (ctxAuth != null && ctxAuth.isAuthenticated()) {
            return ctxAuth;
        }

        // Try to get user from Stomp headers
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        Object user = accessor.getUser();
        if (user instanceof Authentication) {
            return (Authentication) user;
        }

        // Try session attributes
        if (accessor.getSessionAttributes() != null) {
            Object sessionAuth = accessor.getSessionAttributes().get("authentication");
            if (sessionAuth instanceof Authentication) {
                return (Authentication) sessionAuth;
            }
        }

        return null;
    }

    private String senderName(User user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return "Unknown participant";
    }

    private String senderAvatarUrl(User user) {
        return user.getAvatarUrl();
    }
}