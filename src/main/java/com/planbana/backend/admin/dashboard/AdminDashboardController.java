package com.planbana.backend.admin.dashboard;

import com.planbana.backend.audit.AuditLog;
import com.planbana.backend.audit.AuditLogRepository;
import com.planbana.backend.events.Event;
import com.planbana.backend.events.EventRepository;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final UserRepository userRepo;
    private final EventRepository eventRepo;
    private final AuditLogRepository auditLogRepo;

    private static final ZoneId ANALYTICS_ZONE = ZoneId.of("Asia/Kolkata");

    public AdminDashboardController(
            UserRepository userRepo,
            EventRepository eventRepo,
            AuditLogRepository auditLogRepo) {
        this.userRepo = userRepo;
        this.eventRepo = eventRepo;
        this.auditLogRepo = auditLogRepo;
    }

    // ============================================================
    // 1. OVERVIEW SUMMARY
    // ============================================================

    @GetMapping("/overview")
    public Map<String, Object> overview() {

        Instant now = Instant.now();
        Instant last7 = now.minus(7, ChronoUnit.DAYS);
        Instant last30 = now.minus(30, ChronoUnit.DAYS);

        long totalUsers = userRepo.count();
        long verifiedUsers = userRepo.countByGovIdVerificationStatus(User.VerificationStatus.VERIFIED);
        long pendingVerification = userRepo.countByGovIdVerificationStatus(User.VerificationStatus.PENDING);
        long rejectedVerification = userRepo.countByGovIdVerificationStatus(User.VerificationStatus.REJECTED);
        long disabledUsers = userRepo.countByDisabled(true);
        long newUsers7 = userRepo.countByCreatedAtAfter(last7);
        long newUsers30 = userRepo.countByCreatedAtAfter(last30);

        long totalEvents = eventRepo.count();
        long activeEvents = eventRepo.countByStatus(Event.Status.ACTIVE);
        long blockedEvents = eventRepo.countByStatus(Event.Status.BLOCKED);
        long hiddenEvents = eventRepo.countByStatus(Event.Status.HIDDEN);
        long pendingReviewEvents = eventRepo.countByStatus(Event.Status.PENDING_REVIEW);

        long events7 = eventRepo.countByCreatedAtAfter(last7);
        long events30 = eventRepo.countByCreatedAtAfter(last30);

        long upcomingEvents = eventRepo.countByStartAtAfter(now);
        long pastEvents = eventRepo.countByEndAtBefore(now);

        return Map.of(
                "users", Map.of(
                        "total", totalUsers,
                        "verified", verifiedUsers,
                        "pendingVerification", pendingVerification,
                        "rejectedVerification", rejectedVerification,
                        "disabled", disabledUsers,
                        "joinedLast7Days", newUsers7,
                        "joinedLast30Days", newUsers30),

                "events", Map.of(
                        "total", totalEvents,
                        "active", activeEvents,
                        "blocked", blockedEvents,
                        "hidden", hiddenEvents,
                        "pendingReview", pendingReviewEvents,
                        "createdLast7Days", events7,
                        "createdLast30Days", events30,
                        "upcomingNext7Days", upcomingEvents,
                        "pastLast30Days", pastEvents));
    }

    // ============================================================
    // 2. EVENT STATUS DISTRIBUTION
    // ============================================================

    @GetMapping("/event-status-distribution")
    public Map<String, Long> eventStatusDistribution() {
        return Map.of(
                "active", eventRepo.countByStatus(Event.Status.ACTIVE),
                "blocked", eventRepo.countByStatus(Event.Status.BLOCKED),
                "hidden", eventRepo.countByStatus(Event.Status.HIDDEN),
                "pendingReview", eventRepo.countByStatus(Event.Status.PENDING_REVIEW));
    }

    // ============================================================
    // 3. USER VERIFICATION BREAKDOWN
    // ============================================================

    @GetMapping("/user-verification-breakdown")
    public Map<String, Long> userVerificationBreakdown() {
        return Map.of(
                "verified", userRepo.countByGovIdVerificationStatus(User.VerificationStatus.VERIFIED),
                "pending", userRepo.countByGovIdVerificationStatus(User.VerificationStatus.PENDING),
                "rejected", userRepo.countByGovIdVerificationStatus(User.VerificationStatus.REJECTED));
    }

    // ============================================================
    // 4. 30-DAY ACTIVITY STATS
    // ============================================================

    @GetMapping("/activity-stats")
    public Map<String, Object> activityStats() {
        Instant now = Instant.now();
        Instant last30 = now.minus(30, ChronoUnit.DAYS);

        return Map.of(
                "last30DaysUsers", userRepo.countByCreatedAtAfter(last30),
                "last30DaysEvents", eventRepo.countByCreatedAtAfter(last30));
    }

    // ============================================================
    // 5. DAILY USER SIGNUPS (line chart)
    // ============================================================

    @GetMapping("/daily-user-signups")
    public Map<String, Long> dailyUserSignups() {

        Map<String, Long> result = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        for (int i = 30; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

            long count = userRepo.countByCreatedAtBetween(start, end);
            result.put(date.toString(), count);
        }

        return result;
    }

    // ============================================================
    // 6. DAILY EVENTS CREATED (line chart)
    // ============================================================

    @GetMapping("/daily-events-created")
    public Map<String, Long> dailyEventsCreated() {

        Map<String, Long> result = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        for (int i = 30; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

            long count = eventRepo.countByCreatedAtBetween(start, end);
            result.put(date.toString(), count);
        }

        return result;
    }

    // ============================================================
    // 7. TOP EVENT CREATORS
    // ============================================================

    @GetMapping("/top-users")
    public List<Map<String, Object>> topUsers() {

        var users = userRepo.findTop10ByOrderByCreatedAtDesc();

        return users.stream()
                .map(u -> Map.<String, Object>of(
                        "userId", u.getId(),
                        "name", Optional.ofNullable(u.getDisplayName()).orElse("Unknown"),
                        "eventsCreated", eventRepo.countByCreatedByUserId(u.getId()),
                        "joinedAt", u.getCreatedAt()))
                .collect(Collectors.toList());
    }

    // ============================================================
    // 8. ACTIVE USERS SUMMARY (DAU / WAU / MAU)
    // ============================================================

    @GetMapping("/active-users/summary")
    public Map<String, Object> activeUsersSummary() {

        Instant now = Instant.now();
        Instant dayAgo = now.minus(1, ChronoUnit.DAYS);
        Instant weekAgo = now.minus(7, ChronoUnit.DAYS);
        Instant monthAgo = now.minus(30, ChronoUnit.DAYS);

        List<AuditLog> logs = auditLogRepo.findByTimestampBetween(monthAgo, now);

        Set<String> dau = new HashSet<>();
        Set<String> wau = new HashSet<>();
        Set<String> mau = new HashSet<>();

        for (AuditLog log : logs) {
            String userId = log.getPerformedBy();
            if (userId == null || userId.isBlank())
                continue;

            Instant ts = log.getTimestamp();
            if (ts == null)
                continue;

            if (!ts.isBefore(monthAgo))
                mau.add(userId);
            if (!ts.isBefore(weekAgo))
                wau.add(userId);
            if (!ts.isBefore(dayAgo))
                dau.add(userId);
        }

        return Map.of(
                "dau", dau.size(),
                "wau", wau.size(),
                "mau", mau.size());
    }

    // ============================================================
    // 9. DAILY ACTIVE USERS
    // ============================================================

    @GetMapping("/active-users/daily")
    public Map<String, Long> activeUsersDaily() {

        Instant now = Instant.now();
        Instant last30 = now.minus(30, ChronoUnit.DAYS);

        List<AuditLog> logs = auditLogRepo.findByTimestampBetween(last30, now);

        Map<LocalDate, Set<String>> daily = new HashMap<>();

        for (AuditLog log : logs) {
            if (log.getPerformedBy() == null)
                continue;

            LocalDate date = LocalDate.ofInstant(log.getTimestamp(), ANALYTICS_ZONE);
            daily.computeIfAbsent(date, d -> new HashSet<>()).add(log.getPerformedBy());
        }

        Map<String, Long> result = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(ANALYTICS_ZONE);

        for (int i = 30; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            long count = daily.getOrDefault(date, Set.of()).size();
            result.put(date.toString(), count);
        }

        return result;
    }

    // ============================================================
    // 10. WEEKLY ACTIVE USERS
    // ============================================================

    @GetMapping("/active-users/weekly")
    public Map<String, Long> activeUsersWeekly() {

        Instant now = Instant.now();
        Instant last90 = now.minus(90, ChronoUnit.DAYS);

        List<AuditLog> logs = auditLogRepo.findByTimestampBetween(last90, now);

        Map<LocalDate, Set<String>> weekly = new LinkedHashMap<>();

        for (AuditLog log : logs) {
            if (log.getPerformedBy() == null)
                continue;

            LocalDate date = LocalDate.ofInstant(log.getTimestamp(), ANALYTICS_ZONE);
            LocalDate weekStart = date.with(DayOfWeek.MONDAY);

            weekly.computeIfAbsent(weekStart, d -> new HashSet<>()).add(log.getPerformedBy());
        }

        Map<String, Long> result = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(ANALYTICS_ZONE);

        for (int i = 12; i >= 0; i--) {
            LocalDate weekStart = today.minusWeeks(i).with(DayOfWeek.MONDAY);
            long count = weekly.getOrDefault(weekStart, Set.of()).size();
            result.put(weekStart.toString(), count);
        }

        return result;
    }

    // ============================================================
    // 11. MONTHLY ACTIVE USERS
    // ============================================================

    @GetMapping("/active-users/monthly")
    public Map<String, Long> activeUsersMonthly() {

        Instant now = Instant.now();
        Instant last365 = now.minus(365, ChronoUnit.DAYS);

        List<AuditLog> logs = auditLogRepo.findByTimestampBetween(last365, now);

        Map<YearMonth, Set<String>> buckets = new LinkedHashMap<>();

        for (AuditLog log : logs) {
            if (log.getPerformedBy() == null)
                continue;

            YearMonth ym = YearMonth.from(
                    log.getTimestamp().atZone(ANALYTICS_ZONE).toLocalDate());

            buckets.computeIfAbsent(ym, y -> new HashSet<>()).add(log.getPerformedBy());
        }

        Map<String, Long> result = new LinkedHashMap<>();
        YearMonth current = YearMonth.now(ANALYTICS_ZONE);

        for (int i = 12; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            long count = buckets.getOrDefault(ym, Set.of()).size();
            result.put(ym.toString(), count);
        }

        return result;
    }

    // ============================================================
    // 12. LOCATION HEATMAP
    // ============================================================

    @GetMapping("/location-heatmap")
    public Map<String, Object> locationHeatmap(
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {

        Instant now = Instant.now();
        Instant since = now.minus(days, ChronoUnit.DAYS);

        List<Event> events = eventRepo.findAll();

        Map<String, HeatBucket> buckets = new HashMap<>();

        for (Event e : events) {
            if (e.isCanceled())
                continue;
            if (e.getStatus() != Event.Status.ACTIVE)
                continue;
            if (e.getStartAt() != null && e.getStartAt().isBefore(since))
                continue;

            var point = e.getLocation() != null ? e.getLocation() : e.getStartLocation();
            if (point == null)
                continue;

            double lat = point.getY();
            double lng = point.getX();

            double latBucket = Math.round(lat * 100.0) / 100.0;
            double lngBucket = Math.round(lng * 100.0) / 100.0;

            String key = latBucket + "," + lngBucket;

            buckets.computeIfAbsent(key, k -> new HeatBucket(latBucket, lngBucket)).increment();
        }

        List<Map<String, Object>> points = buckets.values().stream()
                .map(b -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("lat", b.lat);
                    m.put("lng", b.lng);
                    m.put("count", b.count);
                    return m;
                })
                .collect(Collectors.toList());

        return Map.of(
                "windowDays", days,
                "points", points);
    }

    // Heatmap helper DTO
    private static class HeatBucket {
        final double lat;
        final double lng;
        long count = 0L;

        HeatBucket(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }

        void increment() {
            this.count++;
        }
    }
}
