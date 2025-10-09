package com.planbana.backend.events;

public class JoinStatusOverview {
    private String eventId;
    private String eventTitle;
    private String status;
    private String redirectToEventPage;
    private String conversationLink; // only if APPROVED

    // Getters & Setters
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public void setEventTitle(String eventTitle) {
        this.eventTitle = eventTitle;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRedirectToEventPage() {
        return redirectToEventPage;
    }

    public void setRedirectToEventPage(String redirectToEventPage) {
        this.redirectToEventPage = redirectToEventPage;
    }

    public String getConversationLink() {
        return conversationLink;
    }

    public void setConversationLink(String conversationLink) {
        this.conversationLink = conversationLink;
    }
}
