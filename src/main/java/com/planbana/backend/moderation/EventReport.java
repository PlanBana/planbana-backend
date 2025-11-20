package com.planbana.backend.moderation;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("event_reports")
public class EventReport {

    @Id
    private String id;

    private String eventId;
    private String reporterUserId;

    private String reason;
    private String category; // spam, abuse, misleading, etc.

    private Instant createdAt = Instant.now();

    // --- Moderation Fields ---
    private Status status = Status.PENDING; // PENDING, REVIEWED, ACTION_TAKEN
    private String reviewedBy; // admin userId
    private Instant reviewedAt;
    private String assignedTo;

    private String adminAction; // BLOCK_EVENT, HIDE_EVENT, WARN_USER
    private String actionReason;

    public enum Status {
        PENDING,
        REVIEWED,
        ACTION_TAKEN
    }

    // ============================
    // Getters / Setters
    // ============================

    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getReporterUserId() {
        return reporterUserId;
    }

    public void setReporterUserId(String reporterUserId) {
        this.reporterUserId = reporterUserId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public String getAdminAction() {
        return adminAction;
    }

    public void setAdminAction(String adminAction) {
        this.adminAction = adminAction;
    }

    public String getActionReason() {
        return actionReason;
    }

    public void setActionReason(String actionReason) {
        this.actionReason = actionReason;
    }
}
