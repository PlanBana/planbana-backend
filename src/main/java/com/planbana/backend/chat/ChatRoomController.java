package com.planbana.backend.chat;

import com.planbana.backend.admin.settings.MaintenanceGuard;
import com.planbana.backend.events.Event;
import com.planbana.backend.events.EventRepository;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import com.planbana.backend.storage.FileStorageService;
import org.springframework.http.ResponseEntity;
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

    private final MaintenanceGuard maintenanceGuard;

    public ChatRoomController(ChatRoomRepository chatRooms,
            MessageRepository messages,
            EventRepository events,
            UserRepository users,
            FileStorageService storage,
            MaintenanceGuard maintenanceGuard) {
        this.chatRooms = chatRooms;
        this.messages = messages;
        this.events = events;
        this.users = users;
        this.storage = storage;
        this.maintenanceGuard = maintenanceGuard;
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

        ChatRoom room = new ChatRoom(event.getId(), event.getTitle(), user.getPhone()); // ✅ use phone for identity
        room.getAdmins().add(user.getPhone());
        room.getAdmins().addAll(event.getCoHostUserIds()); // optional: keep existing cohost ids if phone-based too
        room.getParticipants().add(user.getPhone());
        room.getParticipants().addAll(event.getParticipants()); // consider switching to phone-based later
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
        if (!room.getParticipants().contains(u.getPhone()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not part of chatroom");

        return room;
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
     * Includes sender names for UI
     */
    @GetMapping("/event/{eventId}/messages")
    public List<Message> listMessagesByEvent(@PathVariable String eventId, Authentication auth) {
        ChatRoom room = chatRooms.findByEventId(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chatroom not found"));

        User user = users.findByPhone(auth.getName()).orElseThrow();
        String userId = user.getId();

        if (!room.getParticipants().contains(userId) && !room.getParticipants().contains(user.getPhone())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not in chatroom");
        }

        List<Message> result = messages.findByChatRoomIdOrderBySentAtAsc(room.getId());

        // ✅ Populate sender names for all messages
        for (Message m : result) {
            users.findById(m.getSenderId())
                    .ifPresent(u -> {
                        String name = u.getDisplayName();
                        if (name == null || name.isBlank()) {
                            name = u.getPhone(); // fallback if no display name
                        }
                        m.setSenderName(name);
                    });
        }

        return result;
    }

}
