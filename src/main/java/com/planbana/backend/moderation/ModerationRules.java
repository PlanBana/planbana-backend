package com.planbana.backend.moderation;

/**
 * Central place for all auto-moderation configuration.
 * You can later move this to DB-backed settings if needed.
 */
public final class ModerationRules {

    // ==========================
    // Report / Blocking
    // ==========================
    public static final boolean AUTO_BLOCK_ENABLED = true;
    public static final int REPORT_THRESHOLD = 5; // number of reports before auto-block

    // ==========================
    // Spam / Auto-Hide
    // ==========================
    public static final boolean AUTO_SPAM_HIDE_ENABLED = true;

    // ==========================
    // Auto-Archive old events
    // ==========================
    public static final boolean AUTO_ARCHIVE_ENABLED = true;

    /**
     * After how many days *after event end* we should auto-archive (cancel) it.
     * We are reusing `isCanceled = true` as “archived” flag to avoid changing
     * Event.Status enum.
     */
    public static final int AUTO_ARCHIVE_DAYS_AFTER_END = 30;

    // ==========================
    // Cron / Background jobs
    // ==========================
    public static final boolean CRON_ENABLED = true;

    private ModerationRules() {
        // no instances
    }
}
