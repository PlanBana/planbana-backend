package com.planbana.backend.chat;

import org.springframework.stereotype.Service;

@Service
public class ChatRoomService {

    private final ChatRoomRepository repo;

    public ChatRoomService(ChatRoomRepository repo) {
        this.repo = repo;
    }

    public ChatRoom createIfNotExists(String eventId, String name, String createdBy, String imageUrl) {
        return repo.findByEventId(eventId).orElseGet(() -> {
            ChatRoom chat = new ChatRoom(eventId, name, createdBy);
            chat.setImageUrl(imageUrl);
            chat.getParticipants().add(createdBy);
            return repo.save(chat);
        });
    }

    /** ✅ Safe: adds participant only if chatroom exists and user missing */
    public void addParticipantIfExists(String eventId, String userId) {
        repo.findByEventId(eventId).ifPresent(chat -> {
            if (!chat.getParticipants().contains(userId)) {
                chat.getParticipants().add(userId);
                repo.save(chat);
            }
        });
    }

    /** ✅ Forcefully sync participants from event */
    public void syncParticipantsFromEvent(String eventId, java.util.List<String> eventParticipantIds) {
        repo.findByEventId(eventId).ifPresent(chat -> {
            boolean changed = false;
            for (String id : eventParticipantIds) {
                if (!chat.getParticipants().contains(id)) {
                    chat.getParticipants().add(id);
                    changed = true;
                }
            }
            if (changed) repo.save(chat);
        });
    }
}
