package com.planbana.backend.admin.settings;

import com.planbana.backend.audit.AuditAction;
import com.planbana.backend.audit.AuditCategory;
import com.planbana.backend.audit.AuditLogger;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
public class SystemSettingsController {

    private final SystemSettingsService service;
    private final AuditLogger auditLogger;

    public SystemSettingsController(SystemSettingsService service, AuditLogger auditLogger) {
        this.service = service;
        this.auditLogger = auditLogger;
    }

    // ============================================================
    // 1️⃣ GET GLOBAL SYSTEM SETTINGS
    // ============================================================
    @GetMapping
    public SystemSettings getSettings(Authentication auth) {
        ensureAdmin(auth);

        // Optional: audit that admin viewed settings page
        auditLogger.log(
                AuditCategory.ADMIN_SETTINGS,
                AuditAction.ADMIN_VIEW_EVENT_LIST, // or create: ADMIN_VIEW_SETTINGS if you want
                auth.getName(),
                null,
                "GLOBAL_SETTINGS",
                Map.of("view", "settings"));

        return service.get();
    }

    // ============================================================
    // 2️⃣ UPDATE GLOBAL SETTINGS (partial update)
    // ============================================================
    @PatchMapping
    public SystemSettings updateSettings(
            @RequestBody Map<String, Object> changes,
            Authentication auth) {

        ensureAdmin(auth);

        if (changes == null || changes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No settings provided");
        }

        String adminUserId = auth.getName();

        return service.update(changes, adminUserId);
    }

    // ============================================================
    // HELPER: admin guard
    // ============================================================
    private void ensureAdmin(Authentication auth) {
        if (auth == null || auth.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }
}
