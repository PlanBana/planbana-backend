package com.planbana.backend.chat;

import com.planbana.backend.events.Event;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatRoomService {

    private final ChatRoomRepository repo;
    private final ChatUserStateRepository states;
    private final MessageRepository messages;
    private final UserRepository users;

    public ChatRoomService(ChatRoomRepository repo,
            ChatUserStateRepository states,
            MessageRepository messages,
            UserRepository users) {
        this.repo = repo;
        this.states = states;
        this.messages = messages;
        this.users = users;
    }

    // ============================================================
    // Membership helpers (legacy identity mix: user ids + phones)
    // ============================================================

    /**
     * Membership is keyed by canonical user ID; migrate legacy phone entries first.
     */
    public boolean isMember(ChatRoom room, User user) {
        normalizeParticipantIds(room);
        return room.getParticipants().contains(user.getId());
    }

    public void normalizeParticipantIds(ChatRoom room) {
        if (room.getParticipants() == null || room.getParticipants().isEmpty()) {
            return;
        }

        java.util.Set<String> participantIds = new java.util.HashSet<>();
        boolean changed = false;
        for (String participant : new java.util.HashSet<>(room.getParticipants())) {
            String canonicalId = users.findByPhone(participant)
                    .map(User::getId)
                    .orElseGet(() -> users.findById(participant).map(User::getId).orElse(null));
            if (canonicalId != null) {
                participantIds.add(canonicalId);
                changed |= !canonicalId.equals(participant);
            }
        }

        if (changed || participantIds.size() != room.getParticipants().size()) {
            room.setParticipants(participantIds);
            repo.save(room);
        }
    }

    public boolean canInteract(ChatRoom room, User user) {
        if (!isMember(room, user)) {
            return false;
        }
        return states.findByChatRoomIdAndUserId(room.getId(), user.getId())
                .map(state -> state.getRemovedAt() == null)
                .orElse(true);
    }

    /** Adds the canonical user ID; phone numbers are never persisted as members. */
    private void addParticipant(ChatRoom room, User user) {
        boolean changed = user.getId() != null && !room.getParticipants().contains(user.getId());
        if (changed) {
            room.getParticipants().add(user.getId());
        }
        if (changed)
            repo.save(room);
    }

    // ============================================================
    // Group lifecycle
    // ============================================================

    /** Legacy helper kept for event creation (host-only room). */
    public ChatRoom createIfNotExists(String eventId, String name, String createdBy, String imageUrl) {
        return repo.findByEventId(eventId).orElseGet(() -> {
            String canonicalCreatedBy = users.findById(createdBy)
                    .map(User::getId)
                    .or(() -> users.findByPhone(createdBy).map(User::getId))
                    .orElse(createdBy);
            ChatRoom chat = new ChatRoom(eventId, name, canonicalCreatedBy);
            chat.setImageUrl(imageUrl);
            chat.getParticipants().add(canonicalCreatedBy);
            return repo.save(chat);
        });
    }

    /** Legacy helper kept for external callers. */
    public void addParticipantIfExists(String eventId, String userId) {
        repo.findByEventId(eventId).ifPresent(chat -> {
            String canonicalUserId = users.findById(userId)
                    .map(User::getId)
                    .or(() -> users.findByPhone(userId).map(User::getId))
                    .orElse(userId);
            normalizeParticipantIds(chat);
            if (!chat.getParticipants().contains(canonicalUserId)) {
                chat.getParticipants().add(canonicalUserId);
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
            if (changed)
                repo.save(chat);
        });
    }

    /** Removes the chat group owned by an event, if one exists. */
    public void deleteEventGroup(String eventId) {
        repo.findByEventId(eventId).ifPresent(repo::delete);
    }

    /**
     * Guarantees the event group exists and that the given user is a member.
     * Called whenever the host approves a join request (or someone self-joins
     * approved) so that host + joining user join the chat group automatically.
     * The decided participant threshold is irrelevant here — the group always
     * mirrors ALL accepted participants of the event.
     */
    public ChatRoom ensureEventGroupJoined(Event event, User host, User user) {
        ChatRoom room = repo.findByEventId(event.getId()).orElseGet(() -> {
            ChatRoom chat = new ChatRoom(event.getId(), event.getTitle(), host.getId());
            chat.setImageUrl(event.getImageUrl());

            // Admins: host (both identities) + co-hosts
            chat.getAdmins().add(host.getPhone());
            chat.getAdmins().add(host.getId());
            if (event.getCoHostUserIds() != null) {
                chat.getAdmins().addAll(event.getCoHostUserIds());
            }

            // Default members: host + every accepted participant
            chat.getParticipants().add(host.getId());
            if (event.getParticipants() != null) {
                chat.getParticipants().addAll(event.getParticipants());
            }
            return repo.save(chat);
        });

        boolean changed = false;

        // Host must always be a member (rooms created via event-creation path
        // may predate this and only carry one identity)
        if (!isMember(room, host)) {
            addParticipant(room, host);
            changed = true;
        }

        // The whole group always mirrors the event's accepted participants
        if (event.getParticipants() != null) {
            for (String id : event.getParticipants()) {
                if (!room.getParticipants().contains(id)) {
                    room.getParticipants().add(id);
                    changed = true;
                }
            }
        }

        // The joining user by canonical ID
        if (!isMember(room, user)) {
            addParticipant(room, user);
            changed = true;
        }

        ChatUserState state = getState(room, user);
        if (state.getRemovedAt() != null) {
            state.setRemovedAt(null);
            states.save(state);
        }

        if (changed)
            repo.save(room);
        return room;
    }

    // ============================================================
    // Per-user state (Mark as read / Archive / Mute / Favourite / Clear)
    // ============================================================

    /** Get-or-create the per-user state for a chatroom. */
    public ChatUserState getState(ChatRoom room, User user) {
        return states.findByChatRoomIdAndUserId(room.getId(), user.getId())
                .orElseGet(() -> states.save(new ChatUserState(room.getId(), user.getId())));
    }

    /** Mark as read — newer messages stop counting as unread. */
    public ChatUserState markRead(ChatRoom room, User user) {
        ChatUserState s = getState(room, user);
        s.setLastReadAt(Instant.now());
        return states.save(s);
    }

    /** Partial update — only provided keys are applied. */
    public ChatUserState updateState(ChatRoom room, User user, Map<String, Object> patch) {
        ChatUserState s = getState(room, user);
        if (patch.containsKey("read") && Boolean.TRUE.equals(asBool(patch.get("read")))) {
            s.setLastReadAt(Instant.now());
        }
        if (patch.containsKey("archived")) {
            s.setArchived(Boolean.TRUE.equals(asBool(patch.get("archived"))));
        }
        if (patch.containsKey("muted")) {
            s.setMuted(Boolean.TRUE.equals(asBool(patch.get("muted"))));
        }
        if (patch.containsKey("favourite")) {
            s.setFavourite(Boolean.TRUE.equals(asBool(patch.get("favourite"))));
        }
        return states.save(s);
    }

    /** Clear chat — hides every message older than now for this user only. */
    public ChatUserState clearChat(ChatRoom room, User user) {
        ChatUserState s = getState(room, user);
        s.setClearedAt(Instant.now());
        return states.save(s);
    }

    /** Exit group — removes the user from members and drops their state. */
    public void exitGroup(ChatRoom room, User user) {
        normalizeParticipantIds(room);
        room.getParticipants().remove(user.getId());
        room.getParticipants().remove(user.getPhone());
        repo.save(room);
        states.deleteByChatRoomIdAndUserId(room.getId(), user.getId());
    }

    public void disableParticipant(String eventId, String userId) {
        repo.findByEventId(eventId).ifPresent(room -> users.findById(userId).ifPresent(user -> {
            ChatUserState state = getState(room, user);
            state.setRemovedAt(Instant.now());
            states.save(state);
        }));
    }

    // ============================================================
    // Sidebar payload
    // ============================================================

    /**
     * All chatrooms the user is a member of, with state + unread + last message.
     */
    public List<ChatRoomListItem> listMine(User user) {
        List<ChatRoom> mine = repo.findAll().stream()
                .filter(r -> isMember(r, user))
                .collect(Collectors.toList());

        List<ChatRoomListItem> items = new ArrayList<>();
        for (ChatRoom room : mine) {
            ChatUserState s = getState(room, user);

            ChatRoomListItem item = new ChatRoomListItem();
            item.setId(room.getId());
            item.setEventId(room.getEventId());
            item.setTitle(room.getTitle());
            item.setImageUrl(room.getImageUrl());
            item.setParticipants(new ArrayList<>(room.getParticipants()));
            item.setLastReadAt(s.getLastReadAt());
            item.setArchived(s.isArchived());
            item.setMuted(s.isMuted());
            item.setFavourite(s.isFavourite());
            item.setClearedAt(s.getClearedAt());

            // Unread: messages after max(lastReadAt, clearedAt) not sent by me
            Instant threshold = s.getLastReadAt() == null ? Instant.EPOCH : s.getLastReadAt();
            if (s.getClearedAt() != null && s.getClearedAt().isAfter(threshold)) {
                threshold = s.getClearedAt();
            }
            if (s.getRemovedAt() != null) {
                item.setUnreadCount(0L);
            } else {
                item.setUnreadCount(messages.countByChatRoomIdAndSentAtAfterAndSenderIdNot(
                        room.getId(), threshold, user.getId()));
            }

            // Last visible message (null when cleared beyond it)
            messages.findFirstByChatRoomIdOrderBySentAtDesc(room.getId())
                    .filter(m -> s.getClearedAt() == null || m.getSentAt().isAfter(s.getClearedAt()))
                    .filter(m -> s.getRemovedAt() == null || !m.getSentAt().isAfter(s.getRemovedAt()))
                    .ifPresent(m -> {
                        String name = m.getSenderName();
                        if (name == null || name.isBlank()) {
                            name = findSender(m.getSenderId())
                                    .map(this::senderName)
                                    .orElse(null);
                        }
                        item.setLastMessage(new ChatRoomListItem.LastMessage(
                                name, m.getText(), m.getSentAt()));
                    });

            items.add(item);
        }

        // Favourites first, then newest activity
        items.sort(Comparator
                .comparing((ChatRoomListItem i) -> !i.isFavourite())
                .thenComparing(i -> i.getLastMessage() == null ? Instant.EPOCH : i.getLastMessage().getSentAt()));
        return items;
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

    private Boolean asBool(Object v) {
        if (v instanceof Boolean b)
            return b;
        if (v instanceof String s)
            return Boolean.parseBoolean(s);
        return null;
    }
}
