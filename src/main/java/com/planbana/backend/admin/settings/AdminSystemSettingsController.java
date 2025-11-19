package com.planbana.backend.admin.settings;

import com.planbana.backend.admin.settings.SystemSettings;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/settings")
public class AdminSystemSettingsController {

    private final AdminSystemSettingsService service;

    public AdminSystemSettingsController(AdminSystemSettingsService service) {
        this.service = service;
    }

    private String admin(Authentication auth) {
        return auth.getName(); // phone of admin from JWT
    }

    // Fetch all settings
    @GetMapping
    public SystemSettings get() {
        return service.getSettings();
    }

    // Update system settings
    @PutMapping
    public SystemSettings update(
            @RequestBody SystemSettings settings,
            Authentication auth) {
        return service.update(settings, admin(auth));
    }
}
