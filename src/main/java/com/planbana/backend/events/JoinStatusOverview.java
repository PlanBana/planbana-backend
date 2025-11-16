package com.planbana.backend.events;

public class JoinStatusOverview {
    private String eventId;
    private String eventTitle;
    private String joinStatus;
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

    public String getJoinStatus() {
        return joinStatus;
    }

    public void setJoinStatus(String joinStatus) {
        this.joinStatus = joinStatus;
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
