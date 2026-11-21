package com.planbana.backend.migrations;

import com.planbana.backend.chat.ChatRoom;
import com.planbana.backend.chat.ChatRoomRepository;
import com.planbana.backend.chat.ChatRoomService;
import com.planbana.backend.events.Event;
import com.planbana.backend.events.EventRepository;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Component
public class IdentityReferenceMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(IdentityReferenceMigration.class);

    private final EventRepository events;
    private final ChatRoomRepository chatRooms;
    private final UserRepository users;
    private final ChatRoomService chatRoomService;

    public IdentityReferenceMigration(
            EventRepository events,
            ChatRoomRepository chatRooms,
            UserRepository users,
            ChatRoomService chatRoomService) {
        this.events = events;
        this.chatRooms = chatRooms;
        this.users = users;
        this.chatRoomService = chatRoomService;
    }

    @Override
    public void run(String... args) {
        int eventCount = migrateEvents();
        int roomCount = migrateChatRooms();
        if (eventCount > 0 || roomCount > 0) {
            log.info("Canonicalized {} events and {} chat rooms to user IDs", eventCount, roomCount);
        }
    }

    private int migrateEvents() {
        int changedCount = 0;
        for (Event event : events.findAll()) {
            boolean changed = false;

            String canonicalHostId = canonicalId(event.getCreatedByUserId()).orElse(event.getCreatedByUserId());
            if (!java.util.Objects.equals(canonicalHostId, event.getCreatedByUserId())) {
                event.setCreatedByUserId(canonicalHostId);
                changed = true;
            }

            Set<String> canonicalParticipants = canonicalIds(event.getParticipants());
            if (!canonicalParticipants.equals(event.getParticipants())) {
                event.setParticipants(canonicalParticipants);
                changed = true;
            }

            if (changed) {
                events.save(event);
                changedCount++;
            }
        }
        return changedCount;
    }

    private int migrateChatRooms() {
        int changedCount = 0;
        for (ChatRoom room : chatRooms.findAll()) {
            Set<String> beforeParticipants = room.getParticipants() == null
                    ? Set.of()
                    : new HashSet<>(room.getParticipants());
            String beforeCreator = room.getCreatedByUserId();

            chatRoomService.normalizeParticipantIds(room);
            if (room.getAdmins() != null) {
                room.setAdmins(canonicalIds(room.getAdmins()));
            }

            String canonicalCreator = canonicalId(beforeCreator).orElse(beforeCreator);
            room.setCreatedByUserId(canonicalCreator);

            boolean changed = !beforeParticipants.equals(room.getParticipants())
                    || !java.util.Objects.equals(beforeCreator, canonicalCreator);
            if (changed) {
                chatRooms.save(room);
                changedCount++;
            }
        }
        return changedCount;
    }

    private Set<String> canonicalIds(Set<String> values) {
        Set<String> result = new HashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            canonicalId(value).ifPresent(result::add);
        }
        return result;
    }

    private Optional<String> canonicalId(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return users.findByPhone(value)
                .map(User::getId)
                .or(() -> users.findById(value).map(User::getId));
    }
}
