package com.planbana.backend.chat;

/**
 * Metadata returned after a chat attachment is stored in object storage.
 * The client forwards these fields in the STOMP/WebSocket message payload so
 * the message (with attachment metadata) is persisted and broadcast to all
 * participants.
 */
public class AttachmentUploadResult {

    private String messageType; // IMAGE | FILE
    private String attachmentUrl;
    private String attachmentName;
    private String attachmentMimeType;
    private Long attachmentSize;
    private String attachmentStorageKey;

    public AttachmentUploadResult() {
    }

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
}