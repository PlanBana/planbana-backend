package com.planbana.backend.admin.settings;

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

    // Fetch all system settings
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
