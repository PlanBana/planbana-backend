package com.planbana.backend.moderation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic background runner that triggers auto-moderation logic.
 *
 * NOTE:
 * - Make sure you have @EnableScheduling on your Spring Boot main application
 * (or any @Configuration class), e.g.:
 *
 * @SpringBootApplication
 * @EnableScheduling
 *                   public class Application { ... }
 */
@Component
public class ModerationCron {

    private final AutoModerationService autoModeration;

    public ModerationCron(AutoModerationService autoModeration) {
        this.autoModeration = autoModeration;
    }

    /**
     * Run every hour.
     * You can tweak the cron expression as needed.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void runHourly() {
        if (!ModerationRules.CRON_ENABLED) {
            return;
        }

        // 1) background spam / auto-hide
        autoModeration.runBackgroundChecks();

        // 2) auto-archive old events
        autoModeration.autoArchiveOldEvents();
    }
}
