package com.planbana.backend.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.time.Instant;

@Controller
public class ChatSocketController {

    private final SimpMessagingTemplate template;
    private final MessageRepository messages;
    private final ChatRoomRepository chatRooms;
    private final UserRepository users;

    public ChatSocketController(
            SimpMessagingTemplate template,
            MessageRepository messages,
            ChatRoomRepository chatRooms,
            UserRepository users) {
        this.template = template;
        this.messages = messages;
        this.chatRooms = chatRooms;
        this.users = users;
    }

    @MessageMapping("/chat/{eventId}")
    public void sendMessage(
            @DestinationVariable String eventId,
            @Payload MessagePayload payload,
            Authentication auth) {

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

        // ✅ Find or create chatroom
        ChatRoom room = chatRooms.findByEventId(eventId)
                .orElseGet(() -> {
                    ChatRoom newRoom = new ChatRoom(eventId, "Chat for event " + eventId, userId);
                    newRoom.getParticipants().add(userId);
                    return chatRooms.save(newRoom);
                });

        // ✅ Verify membership
        if (!room.getParticipants().contains(userId)
                && !room.getParticipants().contains(user.getPhone())) {
            System.out.println("⚠️ User " + user.getPhone()
                    + " (id=" + userId + ") not in participants for chatroom " + eventId);
            return;
        }

        // ✅ Always set senderName safely (fallback if blank)
        String displayName = user.getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            displayName = user.getPhone(); // fallback to phone number
        }

        // ✅ Build message
        Message msg = new Message();
        msg.setChatRoomId(room.getId());
        msg.setSenderId(userId);
        msg.setSenderName(displayName);
        msg.setText(payload.getText());
        msg.setSentAt(Instant.now());

        messages.save(msg);

        // ✅ Log full JSON with proper JavaTimeModule (fix for Instant)
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule()); // <-- key fix
            System.out.println("📦 Sending message payload JSON: " +
                    mapper.writeValueAsString(msg));
        } catch (Exception e) {
            System.err.println("⚠️ Failed to serialize message JSON:");
            e.printStackTrace();
        }

        // ✅ Send message via WebSocket
        template.convertAndSend("/topic/chat/" + eventId, msg);
        System.out.println("💬 Message from " + displayName + " saved & sent to /topic/chat/" + eventId);
    }
}
