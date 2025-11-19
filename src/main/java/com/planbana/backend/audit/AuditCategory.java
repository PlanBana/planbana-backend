package com.planbana.backend.audit;

/**
 * High-level audit categories, designed to be shown
 * in UI as namespaced labels (e.g. EVENT.JOIN, ADMIN.USERS).
 */
public enum AuditCategory {

    // ===== Admin =====
    ADMIN_AUTH,
    ADMIN_USERS,
    ADMIN_EVENTS,
    ADMIN_SETTINGS,

    // ===== User =====
    USER_ACCOUNT,
    USER_PROFILE,
    USER_ACTIVITY,

    // ===== Events =====
    EVENT_LIFECYCLE,
    EVENT_JOIN,
    EVENT_LIKE,
    EVENT_PARTICIPANTS,
    EVENT_COHOSTS,

    // ===== System & Security =====
    SECURITY_AUTH,
    SYSTEM_MAINTENANCE,
    SYSTEM_SETTINGS,

    // ===== Integrations & others =====
    INTEGRATION,
    FRAUD,
    PAYMENT,
    ANALYTICS
}
