package com.planbana.backend.chat;

import com.planbana.backend.common.BaseEntity;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.HashSet;
import java.util.Set;

@Document(collection = "chatrooms")
public class ChatRoom extends BaseEntity {

    private String eventId;             // links to Event
    private String title;               // e.g. "Trip to Goa"
    private String bio;
    private String imageUrl;
    private String createdByUserId;
    private Set<String> admins = new HashSet<>();
    private Set<String> participants = new HashSet<>();

    public ChatRoom() {}

    public ChatRoom(String eventId, String title, String createdByUserId) {
        this.eventId = eventId;
        this.title = title;
        this.createdByUserId = createdByUserId;
        this.admins.add(createdByUserId);
    }

    // === Getters and Setters ===
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(String createdByUserId) { this.createdByUserId = createdByUserId; }

    public Set<String> getAdmins() { return admins; }
    public void setAdmins(Set<String> admins) { this.admins = admins; }

    public Set<String> getParticipants() { return participants; }
    public void setParticipants(Set<String> participants) { this.participants = participants; }
}
