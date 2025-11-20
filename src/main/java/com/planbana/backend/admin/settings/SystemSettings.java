package com.planbana.backend.admin.settings;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("system_settings")
public class SystemSettings {

    @Id
    private String id = "GLOBAL"; // Singleton document

    // ================================
    // Maintenance Mode
    // ================================
    private boolean maintenanceMode = false;
    private String maintenanceMessage = "The system is under maintenance.";

    // Global announcement displayed across the app
    private String globalAnnouncement = "";

    // ================================
    // Limits
    // ================================
    private int maxEventParticipants = 50;
    private int maxEventsPerUser = 20;
    private int eventImageMaxSizeMb = 10; // MB

    // ================================
    // Moderation Settings
    // ================================
    private boolean autoBlockFlaggedEvents = false;
    private int maxReportsBeforeBlock = 5;

    // ================================
    // Feature Flags
    // ================================
    private boolean enableEventChat = true;
    private boolean enablePushNotifications = true;
    private boolean enableUserRegistration = true;
    private boolean allowGuestLogin = false;

    // ================================
    // Admin Notifications
    // ================================
    private boolean adminEmailNotificationsEnabled = true;

    // ================================
    // Getters & Setters
    // ================================

    public String getId() {
        return id;
    }

    // Maintenance Mode
    public boolean isMaintenanceMode() {
        return maintenanceMode;
    }

    public void setMaintenanceMode(boolean maintenanceMode) {
        this.maintenanceMode = maintenanceMode;
    }

    public String getMaintenanceMessage() {
        return maintenanceMessage;
    }

    public void setMaintenanceMessage(String maintenanceMessage) {
        this.maintenanceMessage = maintenanceMessage;
    }

    // Announcement
    public String getGlobalAnnouncement() {
        return globalAnnouncement;
    }

    public void setGlobalAnnouncement(String globalAnnouncement) {
        this.globalAnnouncement = globalAnnouncement;
    }

    // Limits
    public int getMaxEventParticipants() {
        return maxEventParticipants;
    }

    public void setMaxEventParticipants(int maxEventParticipants) {
        this.maxEventParticipants = maxEventParticipants;
    }

    public int getMaxEventsPerUser() {
        return maxEventsPerUser;
    }

    public void setMaxEventsPerUser(int maxEventsPerUser) {
        this.maxEventsPerUser = maxEventsPerUser;
    }

    public int getEventImageMaxSizeMb() {
        return eventImageMaxSizeMb;
    }

    public void setEventImageMaxSizeMb(int eventImageMaxSizeMb) {
        this.eventImageMaxSizeMb = eventImageMaxSizeMb;
    }

    // Moderation
    public boolean isAutoBlockFlaggedEvents() {
        return autoBlockFlaggedEvents;
    }

    public void setAutoBlockFlaggedEvents(boolean autoBlockFlaggedEvents) {
        this.autoBlockFlaggedEvents = autoBlockFlaggedEvents;
    }

    public int getMaxReportsBeforeBlock() {
        return maxReportsBeforeBlock;
    }

    public void setMaxReportsBeforeBlock(int maxReportsBeforeBlock) {
        this.maxReportsBeforeBlock = maxReportsBeforeBlock;
    }

    // Feature Flags
    public boolean isEnableEventChat() {
        return enableEventChat;
    }

    public void setEnableEventChat(boolean enableEventChat) {
        this.enableEventChat = enableEventChat;
    }

    public boolean isEnablePushNotifications() {
        return enablePushNotifications;
    }

    public void setEnablePushNotifications(boolean enablePushNotifications) {
        this.enablePushNotifications = enablePushNotifications;
    }

    public boolean isEnableUserRegistration() {
        return enableUserRegistration;
    }

    public void setEnableUserRegistration(boolean enableUserRegistration) {
        this.enableUserRegistration = enableUserRegistration;
    }

    public boolean isAllowGuestLogin() {
        return allowGuestLogin;
    }

    public void setAllowGuestLogin(boolean allowGuestLogin) {
        this.allowGuestLogin = allowGuestLogin;
    }

    // Admin Email Notifications
    public boolean isAdminEmailNotificationsEnabled() {
        return adminEmailNotificationsEnabled;
    }

    public void setAdminEmailNotificationsEnabled(boolean adminEmailNotificationsEnabled) {
        this.adminEmailNotificationsEnabled = adminEmailNotificationsEnabled;
    }
}
