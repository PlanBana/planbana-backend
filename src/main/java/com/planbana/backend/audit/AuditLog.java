package com.planbana.backend.audit;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document("audit_logs")
public class AuditLog {

    @Id
    private String id;

    private AuditCategory category;
    private AuditAction action;

    private String performedBy; // user/admin who triggered
    private String targetUser; // affected user
    private String eventId; // affected event

    private Map<String, Object> meta; // flexible metadata

    private Instant timestamp = Instant.now();

    public AuditLog() {
    }

    public AuditLog(AuditCategory category,
            AuditAction action,
            String performedBy,
            String targetUser,
            String eventId,
            Map<String, Object> meta) {
        this.category = category;
        this.action = action;
        this.performedBy = performedBy;
        this.targetUser = targetUser;
        this.eventId = eventId;
        this.meta = meta;
        this.timestamp = Instant.now();
    }

    public String getId() {
        return id;
    }

    public AuditCategory getCategory() {
        return category;
    }

    public void setCategory(AuditCategory category) {
        this.category = category;
    }

    public AuditAction getAction() {
        return action;
    }

    public void setAction(AuditAction action) {
        this.action = action;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }

    public String getTargetUser() {
        return targetUser;
    }

    public void setTargetUser(String targetUser) {
        this.targetUser = targetUser;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
