package com.planbana.backend.chat;

import com.planbana.backend.common.BaseEntity;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

/**
 * Per-user chat state for one chatroom (WhatsApp-style per-view settings).
 * One document per (chatRoomId, userId) pair — created lazily on first use.
 */
@Document(collection = "chat_user_states")
@CompoundIndex(name = "chat_user_state_idx", def = "{'chatRoomId': 1, 'userId': 1}", unique = true)
public class ChatUserState extends BaseEntity {

    private String chatRoomId;
    private String userId;

    /** Messages after this instant count as unread. */
    private Instant lastReadAt;

    /** Conversation hidden into the archived section. */
    private boolean archived = false;

    /** Unread badge shown muted; no interruption for this conversation. */
    private boolean muted = false;

    /** Pinned to the top of the conversation list. */
    private boolean favourite = false;

    /** Messages older than this are hidden for this user (Clear chat). */
    private Instant clearedAt;

    /**
     * Future messages are hidden after event removal; earlier history remains
     * visible.
     */
    private Instant removedAt;

    public ChatUserState() {
    }

    public ChatUserState(String chatRoomId, String userId) {
        this.chatRoomId = chatRoomId;
        this.userId = userId;
        // Fresh state: nothing unread yet — unread starts counting from now.
        this.lastReadAt = Instant.now();
    }

    public String getChatRoomId() {
        return chatRoomId;
    }

    public void setChatRoomId(String chatRoomId) {
        this.chatRoomId = chatRoomId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Instant getLastReadAt() {
        return lastReadAt;
    }

    public void setLastReadAt(Instant lastReadAt) {
        this.lastReadAt = lastReadAt;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public boolean isFavourite() {
        return favourite;
    }

    public void setFavourite(boolean favourite) {
        this.favourite = favourite;
    }

    public Instant getClearedAt() {
        return clearedAt;
    }

    public void setClearedAt(Instant clearedAt) {
        this.clearedAt = clearedAt;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public void setRemovedAt(Instant removedAt) {
        this.removedAt = removedAt;
    }
}