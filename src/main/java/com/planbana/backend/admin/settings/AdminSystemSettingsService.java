package com.planbana.backend.admin.settings;

import com.planbana.backend.events.audit.AuditLog;
import com.planbana.backend.events.audit.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AdminSystemSettingsService {

    private final SystemSettingsRepository repo;
    private final AuditLogRepository auditRepo;

    public AdminSystemSettingsService(SystemSettingsRepository repo, AuditLogRepository auditRepo) {
        this.repo = repo;
        this.auditRepo = auditRepo;
    }

    /** Automatically create settings if not present. */
    public SystemSettings getSettings() {
        return repo.findById("GLOBAL").orElseGet(() -> repo.save(new SystemSettings()));
    }

    /** Store audit log (SYSTEM_SETTINGS_UPDATED) */
    private void log(String adminPhone, String action) {
        auditRepo.save(new AuditLog(null, action, adminPhone, null));
    }

    /** Update system settings */
    public SystemSettings update(SystemSettings updated, String adminPhone) {
        SystemSettings existing = getSettings();

        // Maintenance
        existing.setMaintenanceMode(updated.isMaintenanceMode());
        existing.setMaintenanceMessage(updated.getMaintenanceMessage());
        existing.setGlobalAnnouncement(updated.getGlobalAnnouncement());

        // Limits
        existing.setMaxEventParticipants(updated.getMaxEventParticipants());
        existing.setMaxEventsPerUser(updated.getMaxEventsPerUser());
        existing.setEventImageMaxSizeMb(updated.getEventImageMaxSizeMb());

        // Moderation
        existing.setAutoBlockFlaggedEvents(updated.isAutoBlockFlaggedEvents());
        existing.setMaxReportsBeforeBlock(updated.getMaxReportsBeforeBlock());

        // Feature flags
        existing.setEnableEventChat(updated.isEnableEventChat());
        existing.setEnablePushNotifications(updated.isEnablePushNotifications());
        existing.setEnableUserRegistration(updated.isEnableUserRegistration());
        existing.setAllowGuestLogin(updated.isAllowGuestLogin());

        // Admin notifications
        existing.setAdminEmailNotificationsEnabled(updated.isAdminEmailNotificationsEnabled());

        repo.save(existing);
        log(adminPhone, "ADMIN_UPDATED_SYSTEM_SETTINGS");
        return existing;
    }
}
