package com.planbana.backend.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.planbana.backend.common.BaseEntity;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "messages")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message extends BaseEntity {

    private String chatRoomId;
    private String senderId;
    private String text;
    private Instant sentAt = Instant.now();

    // ================================
    // Attachments (messageType = TEXT leaves these null)
    // ================================
    private String messageType = "TEXT"; // TEXT | IMAGE | FILE
    private String attachmentUrl;
    private String attachmentName;
    private String attachmentMimeType;
    private Long attachmentSize;
    private String attachmentStorageKey;

    // ================================
    // Reply-to (quote) support
    // Only the id is persisted; the quoted text/sender are resolved when the
    // history is read and populated as transient fields on the response.
    // ================================
    private String replyToMessageId;

    @Transient
    private String replyToSenderName;

    @Transient
    private String replyToText;

    // ================================
    // Soft-delete / edit state
    // ================================
    private boolean deleted = false;
    private boolean edited = false;

    @Transient
    @JsonProperty("senderName") // ✅ Force include in JSON
    private String senderName; // not stored in DB, but serialized

    @Transient
    private String senderAvatarUrl;

    public String getChatRoomId() {
        return chatRoomId;
    }

    public void setChatRoomId(String chatRoomId) {
        this.chatRoomId = chatRoomId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getSenderAvatarUrl() {
        return senderAvatarUrl;
    }

    public void setSenderAvatarUrl(String senderAvatarUrl) {
        this.senderAvatarUrl = senderAvatarUrl;
    }

    // === Attachment Getters & Setters ===

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getAttachmentUrl() {
        return attachmentUrl;
    }

    public void setAttachmentUrl(String attachmentUrl) {
        this.attachmentUrl = attachmentUrl;
    }

    public String getAttachmentName() {
        return attachmentName;
    }

    public void setAttachmentName(String attachmentName) {
        this.attachmentName = attachmentName;
    }

    public String getAttachmentMimeType() {
        return attachmentMimeType;
    }

    public void setAttachmentMimeType(String attachmentMimeType) {
        this.attachmentMimeType = attachmentMimeType;
    }

    public Long getAttachmentSize() {
        return attachmentSize;
    }

    public void setAttachmentSize(Long attachmentSize) {
        this.attachmentSize = attachmentSize;
    }

    public String getAttachmentStorageKey() {
        return attachmentStorageKey;
    }

    public void setAttachmentStorageKey(String attachmentStorageKey) {
        this.attachmentStorageKey = attachmentStorageKey;
    }

    public String getReplyToMessageId() {
        return replyToMessageId;
    }

    public void setReplyToMessageId(String replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }

    public String getReplyToSenderName() {
        return replyToSenderName;
    }

    public void setReplyToSenderName(String replyToSenderName) {
        this.replyToSenderName = replyToSenderName;
    }

    public String getReplyToText() {
        return replyToText;
    }

    public void setReplyToText(String replyToText) {
        this.replyToText = replyToText;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isEdited() {
        return edited;
    }

    public void setEdited(boolean edited) {
        this.edited = edited;
    }
}
