package com.planbana.backend.moderation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ModerationCron {

    private final AutoModerationService autoModeration;

    public ModerationCron(AutoModerationService autoModeration) {
        this.autoModeration = autoModeration;
    }

    // Runs every night at 2 AM
    @Scheduled(cron = "0 0 2 * * *")
    public void runNightlyChecks() {
        autoModeration.runBackgroundChecks();
    }
}
