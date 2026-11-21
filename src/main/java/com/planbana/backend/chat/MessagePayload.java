package com.planbana.backend.chat;

/**
 * Inbound STOMP/WebSocket payload for a chat message.
 * <p>
 * For plain text messages only {@link #text} is set and {@link #messageType}
 * stays {@code TEXT}. For attachments the fields are populated with metadata
 * returned by {@code POST /api/chatrooms/event/{eventId}/attachments}.
 */
public class MessagePayload {
    private String text;
    private String messageType = "TEXT"; // TEXT | IMAGE | FILE
    private String replyToMessageId;
    private String attachmentUrl;
    private String attachmentName;
    private String attachmentMimeType;
    private Long attachmentSize;
    private String attachmentStorageKey;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getReplyToMessageId() { return replyToMessageId; }
    public void setReplyToMessageId(String replyToMessageId) { this.replyToMessageId = replyToMessageId; }

    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }

    public String getAttachmentName() { return attachmentName; }
    public void setAttachmentName(String attachmentName) { this.attachmentName = attachmentName; }

    public String getAttachmentMimeType() { return attachmentMimeType; }
    public void setAttachmentMimeType(String attachmentMimeType) { this.attachmentMimeType = attachmentMimeType; }

    public Long getAttachmentSize() { return attachmentSize; }
    public void setAttachmentSize(Long attachmentSize) { this.attachmentSize = attachmentSize; }

    public String getAttachmentStorageKey() { return attachmentStorageKey; }
    public void setAttachmentStorageKey(String attachmentStorageKey) { this.attachmentStorageKey = attachmentStorageKey; }
}
