// package com.planbana.backend.events.audit;

// import org.springframework.data.annotation.Id;
// import org.springframework.data.mongodb.core.mapping.Document;

// import java.time.Instant;
// import java.util.Map;

// @Document("audit_logs")
// public class AuditLog {

//     @Id
//     private String id;

//     private String eventId;
//     private String action;
//     private String performedBy;
//     private String targetUser;
//     private Instant timestamp = Instant.now();

//     // ✅ No-arg constructor for MongoDB
//     public AuditLog() {
//     }

//     // ✅ Constructor without timestamp (uses default now)
//     public AuditLog(String eventId, String action, String performedBy, String targetUser) {
//         this.eventId = eventId;
//         this.action = action;
//         this.performedBy = performedBy;
//         this.targetUser = targetUser;
//         this.timestamp = Instant.now();
//     }

//     // ✅ Constructor with explicit timestamp
//     public AuditLog(String eventId, String action, String performedBy, String targetUser, Instant timestamp) {
//         this.eventId = eventId;
//         this.action = action;
//         this.performedBy = performedBy;
//         this.targetUser = targetUser;
//         this.timestamp = timestamp;
//     }

//     // Getters and setters...

//     public String getId() {
//         return id;
//     }

//     public String getEventId() {
//         return eventId;
//     }

//     public void setEventId(String eventId) {
//         this.eventId = eventId;
//     }

//     public String getAction() {
//         return action;
//     }

//     public void setAction(String action) {
//         this.action = action;
//     }

//     public String getPerformedBy() {
//         return performedBy;
//     }

//     public void setPerformedBy(String performedBy) {
//         this.performedBy = performedBy;
//     }

//     public String getTargetUser() {
//         return targetUser;
//     }

//     public void setTargetUser(String targetUser) {
//         this.targetUser = targetUser;
//     }

//     public Instant getTimestamp() {
//         return timestamp;
//     }

//     public void setTimestamp(Instant timestamp) {
//         this.timestamp = timestamp;
//     }

// }

package com.planbana.backend.events.audit;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document("audit_logs")
public class AuditLog {

    @Id
    private String id;

    // ---- Core fields (existing) ----
    private String eventId;
    private String action;
    private String performedBy;
    private String targetUser;
    private Instant timestamp = Instant.now();

    // ---- NEW: Namespaced logging support ----
    // e.g. "ADMIN_EVENTS", "ADMIN_USERS", "SECURITY", "SYSTEM", "USER_ACTIVITY"
    private String category;

    // Flexible metadata: anything you want to attach to the log
    private Map<String, Object> meta;

    // ==========================
    // Constructors
    // ==========================

    // ✅ No-arg constructor for MongoDB
    public AuditLog() {
    }

    // ✅ Old constructor (kept for backward compatibility)
    public AuditLog(String eventId, String action, String performedBy, String targetUser) {
        this.eventId = eventId;
        this.action = action;
        this.performedBy = performedBy;
        this.targetUser = targetUser;
        this.timestamp = Instant.now();
    }

    // ✅ Old constructor with explicit timestamp (kept)
    public AuditLog(String eventId, String action, String performedBy, String targetUser, Instant timestamp) {
        this.eventId = eventId;
        this.action = action;
        this.performedBy = performedBy;
        this.targetUser = targetUser;
        this.timestamp = (timestamp != null ? timestamp : Instant.now());
    }

    // ✅ NEW rich constructor (optional)
    public AuditLog(
            String eventId,
            String action,
            String performedBy,
            String targetUser,
            String category,
            Map<String, Object> meta,
            Instant timestamp) {
        this.eventId = eventId;
        this.action = action;
        this.performedBy = performedBy;
        this.targetUser = targetUser;
        this.category = category;
        this.meta = meta;
        this.timestamp = (timestamp != null ? timestamp : Instant.now());
    }

    // ==========================
    // Getters and setters
    // ==========================

    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
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

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    // ---- NEW: category + meta ----

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }
}
