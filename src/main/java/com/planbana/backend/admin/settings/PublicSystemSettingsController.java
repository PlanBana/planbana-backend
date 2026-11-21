package com.planbana.backend.admin.settings;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/public/system-settings")
public class PublicSystemSettingsController {

    private final SystemSettingsService service;

    public PublicSystemSettingsController(SystemSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> get() {
        SystemSettings s = service.get();
        return Map.of(
                "maintenanceMode", s.isMaintenanceMode(),
                "maintenanceMessage", s.getMaintenanceMessage(),
                "globalAnnouncement", s.getGlobalAnnouncement());
    }
}
