package com.planbana.backend.admin.settings;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MaintenanceGuard {

    private final SystemSettingsService settingsService;

    public MaintenanceGuard(SystemSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void blockIfMaintenance(Authentication auth) {
        SystemSettings s = settingsService.get();

        if (!s.isMaintenanceMode()) {
            return;
        }

        boolean isAdmin = auth != null &&
                auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MAINTENANCE_MODE");
        }
    }
}
