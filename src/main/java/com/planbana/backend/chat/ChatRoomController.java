package com.planbana.backend.chat;

import com.planbana.backend.admin.settings.MaintenanceGuard;
import com.planbana.backend.events.Event;
import com.planbana.backend.events.EventRepository;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import com.planbana.backend.storage.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chatrooms")
public class ChatRoomController {

    private final ChatRoomRepository chatRooms;
    private final MessageRepository messages;
    private final EventRepository events;
    private final UserRepository users;
    private final FileStorageService storage;
    private final ChatRoomService service;
    private final SimpMessagingTemplate template;

    private final MaintenanceGuard maintenanceGuard;

    @Value("${file.chat.quota.chatroom-mb:500}")
    private long chatroomQuotaMb;

    @Value("${file.chat.quota.user-mb:2048}")
    private long userQuotaMb;

    public ChatRoomController(ChatRoomRepository chatRooms,
            MessageRepository messages,
            EventRepository events,
            UserRepository users,
            FileStorageService storage,
            ChatRoomService service,
            SimpMessagingTemplate template,
            MaintenanceGuard maintenanceGuard) {
        this.chatRooms = chatRooms;
        this.messages = messages;
        this.events = events;
        this.users = users;
        this.storage = storage;
        this.service = service;
        this.template = template;
        this.maintenanceGuard = maintenanceGuard;
    }

    /**
     * Loads the room and verifies the caller is a member (id OR phone —
     * participant sets historically mix both identities).
     */
    private ChatRoom loadRoomForUser(String chatRoomId, User user) {
        ChatRoom room = chatRooms.findById(chatRoomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chatroom not found"));
        if (!service.canInteract(room, user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not part of chatroom");
        }
        return room;
    }

    /**
     * ✅ Create a chatroom for an event (host-only)
     */
    @PostMapping("/event/{eventId}")
    public ChatRoom createForEvent(@PathVariable String eventId, Authentication auth) {
        maintenanceGuard.blockIfMaintenance(auth);
        Event event = events.findById(eventId).orElseThrow();
        User user = users.findByPhone(auth.getName()).orElseThrow();

        if (!event.isHost(user.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only host can create chatroom");

        if (chatRooms.findByEventId(eventId).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chatroom already exists");

        ChatRoom room = new ChatRoom(event.getId(), event.getTitle(), user.getId());
        room.getAdmins().add(user.getPhone());
        room.getAdmins().addAll(event.getCoHostUserIds()); // optional: keep existing cohost ids if phone-based too
        room.getParticipants().add(user.getId());
        room.getParticipants().addAll(event.getParticipants());
        room.setImageUrl(event.getImageUrl());
        return chatRooms.save(room);
    }

    /**
     * ✅ Fetch chatroom details (only for participants)
     */
    @GetMapping("/event/{eventId}")
    public ChatRoom getChatRoom(@PathVariable String eventId, Authentication auth) {
        ChatRoom room = chatRooms.findByEventId(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chatroom not found"));

        User u = users.findByPhone(auth.getName()).orElseThrow();
        if (!service.isMember(room, u))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not part of chatroom");

        return room;
    }

    /**
     * ✅ Sidebar list — every chatroom the caller is in, with per-user state,
     * unread count and last message.
     */
    @GetMapping("/mine")
    public List<ChatRoomListItem> myChatRooms(Authentication auth) {
        User user = users.findByPhone(auth.getName()).orElseThrow();
        return service.listMine(user);
    }

    // ============================================================
    // 3-dot menu actions (per-user, per-room)
    // ============================================================

    /** Mark as read / Archive / Unarchive / Mute / Unmute / Favourite. */
    @PatchMapping("/{chatRoomId}/state")
    public ChatUserState updateState(@PathVariable String chatRoomId,
            @RequestBody Map<String, Object> patch,
            Authentication auth) {
        User user = users.findByPhone(auth.getName()).orElseThrow();
        ChatRoom room = loadRoomForUser(chatRoomId, user);
        return service.updateState(room, user, patch);
    }

    /** Clear chat — hides all messages up to now for this user only. */
    @PostMapping("/{chatRoomId}/clear")
    public ChatUserState clearChat(@PathVariable String chatRoomId, Authentication auth) {
        User user = users.findByPhone(auth.getName()).orElseThrow();
        ChatRoom room = loadRoomForUser(chatRoomId, user);
        return service.clearChat(room, user);
    }

    /** Exit group — removes the caller from members (host included). */
    @PostMapping("/{chatRoomId}/exit")
    public Map<String, String> exitGroup(@PathVariable String chatRoomId, Authentication auth) {
        maintenanceGuard.blockIfMaintenance(auth);
        User user = users.findByPhone(auth.getName()).orElseThrow();
        ChatRoom room = loadRoomForUser(chatRoomId, user);
        service.exitGroup(room, user);
        return Map.of("message", "You have left the group");
    }

    /**
     * ✅ Update chatroom profile (admins only)
     */
    @PatchMapping("/{chatRoomId}/profile")
    public ChatRoom updateProfile(@PathVariable String chatRoomId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String bio,
            Authentication auth) {

        maintenanceGuard.blockIfMaintenance(auth);

        ChatRoom room = chatRooms.findById(chatRoomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        User user = users.findByPhone(auth.getName()).orElseThrow();
        if (!room.getAdmins().contains(user.getPhone()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admins only");

        if (title != null)
            room.setTitle(title);
        if (bio != null)
            room.setBio(bio);

        return chatRooms.save(room);
    }

    /**
     * ✅ Upload chatroom image (admins only)
     */
    @PostMapping("/{chatRoomId}/image")
    public ResponseEntity<Map<String, String>> uploadDp(@PathVariable String chatRoomId,
            @RequestParam("file") MultipartFile file,
            Authentication auth) throws IOException {

        maintenanceGuard.blockIfMaintenance(auth);
        ChatRoom room = chatRooms.findById(chatRoomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        User user = users.findByPhone(auth.getName()).orElseThrow();
        if (!room.getAdmins().contains(user.getPhone()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admins only");

        String url = storage.saveChatroomImage(file, chatRoomId);
        room.setImageUrl(url);
        chatRooms.save(room);
        return ResponseEntity.ok(Map.of("imageUrl", url));
    }

    /**
     * ✅ List messages for an event chatroom
     * - membership accepts user id OR phone (legacy mixed identity)
     * - honours the caller's "Clear chat" watermark
     * - auto-marks the room as read
     */
    @GetMapping("/event/{eventId}/messages")
    public List<Message> listMessagesByEvent(@PathVariable String eventId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication auth) {
        ChatRoom room = chatRooms.findByEventId(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chatroom not found"));

        User user = users.findByPhone(auth.getName()).orElseThrow();

        if (!service.isMember(room, user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not in chatroom");
        }

        ChatUserState state = service.getState(room, user);
        java.time.Instant clearedAt = state.getClearedAt();

        // Optional pagination: /messages?page=0&size=50. When omitted, the full
        // history is returned (backward compatible). Slices are fetched newest-first
        // then reversed so the response is chronological for display.
        List<Message> result;
        if (size != null && size > 0) {
            int p = page == null || page < 0 ? 0 : page;
            int s = Math.min(size, 200);
            result = new java.util.ArrayList<>(
                    messages.findByChatRoomIdOrderBySentAtDesc(room.getId(), PageRequest.of(p, s)));
            java.util.Collections.reverse(result);
        } else {
            result = new java.util.ArrayList<>(messages.findByChatRoomIdOrderBySentAtAsc(room.getId()));
        }
        if (clearedAt != null) {
            result.removeIf(m -> m.getSentAt() == null || !m.getSentAt().isAfter(clearedAt));
        }
        java.time.Instant removedAt = service.getState(room, user).getRemovedAt();
        if (removedAt != null) {
            result.removeIf(m -> m.getSentAt() != null && m.getSentAt().isAfter(removedAt));
        }

        // ✅ Populate sender names for all messages
        for (Message m : result) {
            findSender(m.getSenderId())
                    .ifPresentOrElse(u -> {
                        m.setSenderName(senderName(u));
                        m.setSenderAvatarUrl(senderAvatarUrl(u));
                    }, () -> m.setSenderName("Unknown participant"));
        }

        // ✅ Populate reply-to quote context by resolving the original message
        // from the same history (the original is left unchanged, never replaced).
        Map<String, Message> byId = new java.util.HashMap<>();
        for (Message m : result) {
            byId.put(m.getId(), m);
        }
        for (Message m : result) {
            if (m.getReplyToMessageId() == null || m.getReplyToMessageId().isBlank()) {
                continue;
            }
            Message original = byId.get(m.getReplyToMessageId());
            if (original == null) {
                continue;
            }
            m.setReplyToText(original.getText());
            String originalName = original.getSenderName();
            if (originalName == null || originalName.isBlank()) {
                originalName = findSender(original.getSenderId())
                        .map(this::senderName)
                        .orElse("Unknown participant");
            }
            m.setReplyToSenderName(originalName);
        }

        // ✅ Opening the thread marks it read
        service.markRead(room, user);

        return result;
    }

    private java.util.Optional<User> findSender(String senderId) {
        if (senderId == null || senderId.isBlank()) {
            return java.util.Optional.empty();
        }
        return users.findById(senderId)
                .or(() -> users.findByPhone(senderId))
                .or(() -> users.findByFirebaseUid(senderId));
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

    // ============================================================
    // Chat attachment upload
    // ============================================================

    /**
     * ✅ Upload a chat attachment for an event.
     *
     * Validates auth, event membership, MIME type / file content and storage
     * quotas, then stores the file in object storage. It returns the attachment
     * metadata — the client forwards it in a message over the chat WebSocket so
     * the message (with metadata) is persisted and broadcast to participants.
     *
     * Response body matches the STOMP payload contract, e.g.:
     *
     * <pre>
     * { "messageType":"IMAGE", "attachmentUrl":"/uploads/...",
     *   "attachmentName":"photo.jpg", "attachmentMimeType":"image/jpeg",
     *   "attachmentSize":245678, "attachmentStorageKey":"chat_attachments/..." }
     * </pre>
     */
    @PostMapping("/event/{eventId}/attachments")
    public AttachmentUploadResult uploadAttachment(@PathVariable String eventId,
            @RequestParam("file") MultipartFile file,
            Authentication auth) throws IOException {

        maintenanceGuard.blockIfMaintenance(auth);
        Event event = events.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

        User user = users.findByPhone(auth.getName()).orElseThrow();

        ChatRoom room = chatRooms.findByEventId(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chatroom not found"));

        if (!service.canInteract(room, user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not part of chatroom");
        }

        // Cancelled events are host-broadcast only — members can't upload
        // attachments either (they would otherwise bypass the socket gate).
        if (event.isCanceled() && !event.isHost(user.getId()) && !event.isCoHost(user.getId())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Event was cancelled — only the host can message here");
        }

        // Storage quotas (files are stored in object storage, DB keeps only metadata)
        enforceQuotas(room, user, file.getSize());

        return storage.saveChatAttachment(file, room.getId());
    }

    private void enforceQuotas(ChatRoom room, User user, long incomingBytes) {
        long roomUsedBytes = sumAttachmentSize(messages.findByChatRoomIdAndAttachmentSizeNotNull(room.getId()));
        long userUsedBytes = sumAttachmentSize(messages.findBySenderIdAndAttachmentSizeNotNull(user.getId()));

        long roomLimit = chatroomQuotaMb * 1024L * 1024L;
        if (roomUsedBytes + incomingBytes > roomLimit) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "CHATROOM_STORAGE_QUOTA_EXCEEDED");
        }

        long userLimit = userQuotaMb * 1024L * 1024L;
        if (userUsedBytes + incomingBytes > userLimit) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "USER_STORAGE_QUOTA_EXCEEDED");
        }
    }

    private static long sumAttachmentSize(List<Message> messages) {
        long total = 0L;
        for (Message m : messages) {
            if (m.getAttachmentSize() != null) {
                total += m.getAttachmentSize();
            }
        }
        return total;
    }

    // ============================================================
    // Message edit / delete (soft delete)
    // ============================================================

    /**
     * PATCH /api/chatrooms/event/{eventId}/messages/{messageId} body: {"text":
     * "..."}
     * Sender-only edit. Marks the message edited and broadcasts MESSAGE_EDITED to
     * {@code /topic/chat/events/{eventId}} (the original thread row is updated in
     * place, never duplicated).
     */
    @PatchMapping("/event/{eventId}/messages/{messageId}")
    public Message editMessage(@PathVariable String eventId,
            @PathVariable String messageId,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        maintenanceGuard.blockIfMaintenance(auth);
        ChatRoom room = requireEventRoom(eventId);
        User user = users.findByPhone(auth.getName()).orElseThrow();
        if (!service.canInteract(room, user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not part of chatroom");
        }

        Message m = requireChatMessage(room, messageId);
        if (m.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Message deleted");
        }
        if (!isSenderOrAdmin(m, user, room)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the sender can edit");
        }
        String text = body == null ? null : body.get("text");
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text is required");
        }

        m.setText(text);
        m.setEdited(true);
        messages.save(m);
        populateSender(m);
        broadcastMessageEvent(eventId, "MESSAGE_EDITED", m);
        return m;
    }

    /**
     * DELETE /api/chatrooms/event/{eventId}/messages/{messageId}
     * Sender or room admin. Soft-deletes the message (content cleared) and
     * broadcasts MESSAGE_DELETED to {@code /topic/chat/events/{eventId}}.
     */
    @DeleteMapping("/event/{eventId}/messages/{messageId}")
    public ResponseEntity<Map<String, String>> deleteMessage(@PathVariable String eventId,
            @PathVariable String messageId,
            Authentication auth) {
        maintenanceGuard.blockIfMaintenance(auth);
        ChatRoom room = requireEventRoom(eventId);
        User user = users.findByPhone(auth.getName()).orElseThrow();
        if (!service.canInteract(room, user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not part of chatroom");
        }

        Message m = requireChatMessage(room, messageId);
        if (m.isDeleted()) {
            return ResponseEntity.ok(Map.of("message", "Already deleted"));
        }
        if (!isSenderOrAdmin(m, user, room)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the sender or an admin can delete");
        }

        m.setDeleted(true);
        m.setText(null);
        m.setMessageType(null);
        m.setAttachmentUrl(null);
        m.setAttachmentName(null);
        m.setAttachmentMimeType(null);
        m.setAttachmentSize(null);
        m.setAttachmentStorageKey(null);
        messages.save(m);
        broadcastMessageEvent(eventId, "MESSAGE_DELETED", m);
        return ResponseEntity.ok(Map.of("message", "Message deleted"));
    }

    private ChatRoom requireEventRoom(String eventId) {
        return chatRooms.findByEventId(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chatroom not found"));
    }

    private Message requireChatMessage(ChatRoom room, String messageId) {
        Message m = messages.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        if (!room.getId().equals(m.getChatRoomId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not in chatroom");
        }
        return m;
    }

    private boolean isSenderOrAdmin(Message m, User user, ChatRoom room) {
        boolean sender = (user.getId() != null && user.getId().equals(m.getSenderId()))
                || (user.getPhone() != null && user.getPhone().equals(m.getSenderId()));
        boolean admin = room.getAdmins() != null && room.getAdmins().contains(user.getPhone());
        return sender || admin;
    }

    /**
     * Fills transient sender display fields so a returned Message is JSON-complete.
     */
    private void populateSender(Message m) {
        findSender(m.getSenderId())
                .ifPresentOrElse(u -> {
                    m.setSenderName(senderName(u));
                    m.setSenderAvatarUrl(senderAvatarUrl(u));
                }, () -> m.setSenderName("Unknown participant"));
    }

    /**
     * Relays message update events (edit/delete) to subscribers of
     * {@code /topic/chat/events/{eventId}} so clients can update the existing
     * message in place without inserting a duplicate.
     */
    private void broadcastMessageEvent(String eventId, String event, Message message) {
        template.convertAndSend("/topic/chat/events/" + eventId,
                Map.of("event", event, "message", message));
    }

}
