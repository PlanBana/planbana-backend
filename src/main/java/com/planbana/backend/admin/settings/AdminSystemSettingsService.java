package com.planbana.backend.admin.settings;

import com.planbana.backend.audit.AuditLogger;
import com.planbana.backend.audit.AuditAction;
import com.planbana.backend.audit.AuditCategory;
import org.springframework.stereotype.Service;

@Service
public class AdminSystemSettingsService {

    private final SystemSettingsRepository repo;
    private final AuditLogger audit;

    public AdminSystemSettingsService(SystemSettingsRepository repo, AuditLogger audit) {
        this.repo = repo;
        this.audit = audit;
    }

    public SystemSettings getSettings() {
        return repo.findById("GLOBAL").orElseGet(() -> repo.save(new SystemSettings()));
    }

    public SystemSettings update(SystemSettings updated, String adminPhone) {
        SystemSettings s = getSettings();

        // assign updated values:
        s.setMaintenanceMode(updated.isMaintenanceMode());
        s.setMaintenanceMessage(updated.getMaintenanceMessage());
        s.setGlobalAnnouncement(updated.getGlobalAnnouncement());
        s.setMaxEventParticipants(updated.getMaxEventParticipants());
        s.setMaxEventsPerUser(updated.getMaxEventsPerUser());
        s.setEventImageMaxSizeMb(updated.getEventImageMaxSizeMb());
        s.setAutoBlockFlaggedEvents(updated.isAutoBlockFlaggedEvents());
        s.setMaxReportsBeforeBlock(updated.getMaxReportsBeforeBlock());
        s.setEnableEventChat(updated.isEnableEventChat());
        s.setEnablePushNotifications(updated.isEnablePushNotifications());
        s.setEnableUserRegistration(updated.isEnableUserRegistration());
        s.setAllowGuestLogin(updated.isAllowGuestLogin());
        s.setAdminEmailNotificationsEnabled(updated.isAdminEmailNotificationsEnabled());

        repo.save(s);

        audit.system(
                AuditAction.SYSTEM_SETTINGS_UPDATED,
                adminPhone);

        return s;
    }
}
