package com.planbana.backend.chat;

import java.time.Instant;
import java.util.List;

/**
 * Chatroom row for the "GET /api/chatrooms/mine" sidebar payload — the room
 * flattened with the caller's per-user state, unread count and last message.
 */
public class ChatRoomListItem {

    private String id;
    private String eventId;
    private String title;
    private String imageUrl;

    // members of this group (mixed legacy identity: user ids + phones)
    private List<String> participants;

    // ---- per-user state ----
    private Instant lastReadAt;
    private boolean archived;
    private boolean muted;
    private boolean favourite;
    private Instant clearedAt;

    // ---- computed ----
    private long unreadCount;
    private LastMessage lastMessage;

    public static class LastMessage {
        private String senderName;
        private String text;
        private Instant sentAt;

        public LastMessage(String senderName, String text, Instant sentAt) {
            this.senderName = senderName;
            this.text = text;
            this.sentAt = sentAt;
        }

        public String getSenderName() { return senderName; }
        public String getText() { return text; }
        public Instant getSentAt() { return sentAt; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public List<String> getParticipants() { return participants; }
    public void setParticipants(List<String> participants) { this.participants = participants; }

    public Instant getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(Instant lastReadAt) { this.lastReadAt = lastReadAt; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }

    public boolean isFavourite() { return favourite; }
    public void setFavourite(boolean favourite) { this.favourite = favourite; }

    public Instant getClearedAt() { return clearedAt; }
    public void setClearedAt(Instant clearedAt) { this.clearedAt = clearedAt; }

    public long getUnreadCount() { return unreadCount; }
    public void setUnreadCount(long unreadCount) { this.unreadCount = unreadCount; }

    public LastMessage getLastMessage() { return lastMessage; }
    public void setLastMessage(LastMessage lastMessage) { this.lastMessage = lastMessage; }
}