package com.planbana.backend.admin.settings;

import com.planbana.backend.audit.AuditAction;
import com.planbana.backend.audit.AuditCategory;
import com.planbana.backend.audit.AuditLogger;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class SystemSettingsService {

    private final SystemSettingsRepository repo;
    private final AuditLogger auditLogger;

    public SystemSettingsService(SystemSettingsRepository repo, AuditLogger auditLogger) {
        this.repo = repo;
        this.auditLogger = auditLogger;
    }

    // Always returns GLOBAL singleton record
    public SystemSettings get() {
        return repo.findById("GLOBAL").orElseGet(() -> {
            SystemSettings s = new SystemSettings();
            repo.save(s);
            return s;
        });
    }

    // =========================================================================
    // UPDATE SETTINGS (ADMIN)
    // =========================================================================
    public SystemSettings update(Map<String, Object> changes, String adminUserId) {

        SystemSettings settings = get();
        Map<String, Object> meta = new HashMap<>();

        // Default audit action (may be overridden)
        AuditAction auditAction = AuditAction.ADMIN_UPDATED_SYSTEM_SETTINGS;

        // ========== Maintenance Mode ==========
        if (changes.containsKey("maintenanceMode")) {
            boolean newValue = (Boolean) changes.get("maintenanceMode");
            boolean oldValue = settings.isMaintenanceMode();

            if (newValue != oldValue) {
                settings.setMaintenanceMode(newValue);
                meta.put("maintenanceMode", Map.of("old", oldValue, "new", newValue));
                auditAction = AuditAction.ADMIN_UPDATED_MAINTENANCE_MODE;
            }
        }

        if (changes.containsKey("maintenanceMessage")) {
            String newValue = (String) changes.get("maintenanceMessage");
            String oldValue = settings.getMaintenanceMessage();

            if (!newValue.equals(oldValue)) {
                settings.setMaintenanceMessage(newValue);
                meta.put("maintenanceMessage", Map.of("old", oldValue, "new", newValue));
                auditAction = AuditAction.ADMIN_UPDATED_MAINTENANCE_MODE;
            }
        }

        // ========== Announcement ==========

        if (changes.containsKey("globalAnnouncement")) {
            String newValue = (String) changes.get("globalAnnouncement");
            String oldValue = settings.getGlobalAnnouncement();

            if (!newValue.equals(oldValue)) {
                settings.setGlobalAnnouncement(newValue);
                meta.put("globalAnnouncement", Map.of("old", oldValue, "new", newValue));
                auditAction = AuditAction.ADMIN_UPDATED_ANNOUNCEMENT;
            }
        }

        // ========== Limits ==========

        if (changes.containsKey("maxEventParticipants")) {
            int newValue = (Integer) changes.get("maxEventParticipants");
            int oldValue = settings.getMaxEventParticipants();

            if (newValue != oldValue) {
                settings.setMaxEventParticipants(newValue);
                meta.put("maxEventParticipants", Map.of("old", oldValue, "new", newValue));
                auditAction = AuditAction.ADMIN_UPDATED_LIMITS;
            }
        }

        if (changes.containsKey("maxEventsPerUser")) {
            int newValue = (Integer) changes.get("maxEventsPerUser");
            int oldValue = settings.getMaxEventsPerUser();

            if (newValue != oldValue) {
                settings.setMaxEventsPerUser(newValue);
                meta.put("maxEventsPerUser", Map.of("old", oldValue, "new", newValue));
                auditAction = AuditAction.ADMIN_UPDATED_LIMITS;
            }
        }

        if (changes.containsKey("eventImageMaxSizeMb")) {
            int newValue = (Integer) changes.get("eventImageMaxSizeMb");
            int oldValue = settings.getEventImageMaxSizeMb();

            if (newValue != oldValue) {
                settings.setEventImageMaxSizeMb(newValue);
                meta.put("eventImageMaxSizeMb", Map.of("old", oldValue, "new", newValue));
                auditAction = AuditAction.ADMIN_UPDATED_LIMITS;
            }
        }

        // ========== Moderation ==========

        if (changes.containsKey("autoBlockFlaggedEvents")) {
            boolean newValue = (Boolean) changes.get("autoBlockFlaggedEvents");
            boolean oldValue = settings.isAutoBlockFlaggedEvents();

            if (newValue != oldValue) {
                settings.setAutoBlockFlaggedEvents(newValue);
                meta.put("autoBlockFlaggedEvents", Map.of("old", oldValue, "new", newValue));
                auditAction = AuditAction.ADMIN_UPDATED_SYSTEM_SETTINGS;
            }
        }

        if (changes.containsKey("maxReportsBeforeBlock")) {
            int newValue = (Integer) changes.get("maxReportsBeforeBlock");
            int oldValue = settings.getMaxReportsBeforeBlock();

            if (newValue != oldValue) {
                settings.setMaxReportsBeforeBlock(newValue);
                meta.put("maxReportsBeforeBlock", Map.of("old", oldValue, "new", newValue));
                auditAction = AuditAction.ADMIN_UPDATED_SYSTEM_SETTINGS;
            }
        }

        // ========== Feature Flags ==========

        boolean featureUpdated = false;

        featureUpdated |= applyFlag("enableEventChat", settings::isEnableEventChat, settings::setEnableEventChat,
                changes, meta);
        featureUpdated |= applyFlag("enablePushNotifications", settings::isEnablePushNotifications,
                settings::setEnablePushNotifications, changes, meta);
        featureUpdated |= applyFlag("enableUserRegistration", settings::isEnableUserRegistration,
                settings::setEnableUserRegistration, changes, meta);
        featureUpdated |= applyFlag("allowGuestLogin", settings::isAllowGuestLogin, settings::setAllowGuestLogin,
                changes, meta);
        featureUpdated |= applyFlag("adminEmailNotificationsEnabled", settings::isAdminEmailNotificationsEnabled,
                settings::setAdminEmailNotificationsEnabled, changes, meta);

        if (featureUpdated) {
            auditAction = AuditAction.ADMIN_UPDATED_FEATURE_FLAGS;
        }

        // Save changes
        repo.save(settings);

        // Write audit log
        if (!meta.isEmpty()) {
            auditLogger.log(
                    AuditCategory.ADMIN_SETTINGS,
                    auditAction,
                    adminUserId,
                    null,
                    "GLOBAL_SETTINGS",
                    meta);
        }

        return settings;
    }

    // =========================================================================
    // Helper: Apply boolean flag changes
    // =========================================================================
    private boolean applyFlag(
            String key,
            java.util.function.Supplier<Boolean> getter,
            java.util.function.Consumer<Boolean> setter,
            Map<String, Object> changes,
            Map<String, Object> meta) {
        if (!changes.containsKey(key))
            return false;

        boolean newValue = (Boolean) changes.get(key);
        boolean oldValue = getter.get();

        if (newValue != oldValue) {
            setter.accept(newValue);
            meta.put(key, Map.of("old", oldValue, "new", newValue));
            return true;
        }
        return false;
    }
}
