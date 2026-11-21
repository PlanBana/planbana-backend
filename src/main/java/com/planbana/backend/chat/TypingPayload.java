package com.planbana.backend.chat;

/**
 * Inbound STOMP/WebSocket payload for the typing indicator:
 * {@code {"typing": true|false}} sent to {@code /app/chat/typing/{eventId}}.
 * It is a transient presence signal and is never persisted or broadcast to
 * anyone but the room's subscribers on {@code /topic/chat/typing/{eventId}}.
 */
public class TypingPayload {
    private boolean typing;

    public boolean isTyping() {
        return typing;
    }

    public void setTyping(boolean typing) {
        this.typing = typing;
    }
}