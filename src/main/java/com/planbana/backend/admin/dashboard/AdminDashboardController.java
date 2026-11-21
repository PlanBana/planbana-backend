package com.planbana.backend.admin.dashboard;

import com.planbana.backend.audit.AuditAction;
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
import java.time.temporal.IsoFields;
import java.time.temporal.WeekFields;
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

    // 👇👇👇👇
    // ============================================================
    // 12. RETENTION SUMMARY (D1 / D7 / D30)
    // ============================================================

    @GetMapping("/retention/summary")
    public Map<String, Object> retentionSummary() {

        Instant now = Instant.now();
        Instant dayAgo = now.minus(1, ChronoUnit.DAYS);
        Instant weekAgo = now.minus(7, ChronoUnit.DAYS);
        Instant monthAgo = now.minus(30, ChronoUnit.DAYS);

        // FIXED — Added missing repo method
        List<User> newUsers = userRepo.findByCreatedAtAfter(monthAgo);

        List<AuditLog> logs = auditLogRepo.findByTimestampBetween(monthAgo, now);

        Set<String> active1d = logs.stream()
                .filter(l -> l.getTimestamp().isAfter(dayAgo))
                .map(AuditLog::getPerformedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> active7d = logs.stream()
                .filter(l -> l.getTimestamp().isAfter(weekAgo))
                .map(AuditLog::getPerformedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> active30d = logs.stream()
                .map(AuditLog::getPerformedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        long totalNew = newUsers.size();
        long d1 = newUsers.stream().filter(u -> active1d.contains(u.getId())).count();
        long d7 = newUsers.stream().filter(u -> active7d.contains(u.getId())).count();
        long d30 = newUsers.stream().filter(u -> active30d.contains(u.getId())).count();

        return Map.of(
                "newUsers", totalNew,
                "D1", totalNew == 0 ? 0 : Math.round(d1 * 100.0 / totalNew),
                "D7", Math.round(d7 * 100.0 / totalNew),
                "D30", Math.round(d30 * 100.0 / totalNew));
    }

    // ============================================================
    // 13. RETENTION COHORTS (weekly)
    // ============================================================

    @GetMapping("/retention/cohorts")
    public List<Map<String, Object>> retentionCohorts() {

        // FIXED — YearWeek helper
        Map<YearWeek, List<User>> cohorts = userRepo.findAll().stream()
                .collect(Collectors
                        .groupingBy(u -> YearWeek.from(u.getCreatedAt().atZone(ANALYTICS_ZONE).toLocalDate())));

        Instant now = Instant.now();
        Instant last90 = now.minus(90, ChronoUnit.DAYS);

        List<AuditLog> logs = auditLogRepo.findByTimestampBetween(last90, now);

        List<Map<String, Object>> result = new ArrayList<>();

        // FIXED — iterate Map.Entry
        for (Map.Entry<YearWeek, List<User>> entry : cohorts.entrySet()) {

            YearWeek week = entry.getKey();
            List<User> users = entry.getValue();

            Set<String> userIds = users.stream().map(User::getId).collect(Collectors.toSet());

            long d1 = logs.stream()
                    .filter(l -> userIds.contains(l.getPerformedBy()))
                    .filter(l -> l.getTimestamp().isBefore(
                            weekStart(week).plus(1, ChronoUnit.DAYS)))
                    .count();

            long d7 = logs.stream()
                    .filter(l -> userIds.contains(l.getPerformedBy()))
                    .filter(l -> l.getTimestamp().isBefore(
                            weekStart(week).plus(7, ChronoUnit.DAYS)))
                    .count();

            long d30 = logs.stream()
                    .filter(l -> userIds.contains(l.getPerformedBy()))
                    .filter(l -> l.getTimestamp().isBefore(
                            weekStart(week).plus(30, ChronoUnit.DAYS)))
                    .count();

            result.add(Map.of(
                    "week", week.toString(),
                    "size", users.size(),
                    "D1", d1,
                    "D7", d7,
                    "D30", d30));
        }

        // Sort by week
        result.sort(Comparator.comparing(row -> (String) row.get("week")));
        return result;
    }

    // Helper: start of cohort week
    private Instant weekStart(YearWeek w) {
        LocalDate first = LocalDate.of(w.year(), 1, 4); // ISO week anchor
        return first.with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, w.week())
                .with(DayOfWeek.MONDAY)
                .atStartOfDay(ANALYTICS_ZONE).toInstant();
    }

    // ========================================
    // Helper DTO: YearWeek (YYYY-WW)
    // ========================================
    private record YearWeek(int year, int week) {

        static YearWeek from(LocalDate date) {
            var iso = WeekFields.ISO;
            int week = date.get(iso.weekOfWeekBasedYear());
            int year = date.get(IsoFields.WEEK_BASED_YEAR);
            return new YearWeek(year, week);
        }

        @Override
        public String toString() {
            return year + "-W" + String.format("%02d", week);
        }
    }

    // ============================================================
    // B. USER ACTIVITY HISTOGRAM (0–23 hours)
    // ============================================================

    @GetMapping("/activity/histogram")
    public Map<String, Object> userActivityHistogram(
            @RequestParam(defaultValue = "7") int days) {

        Instant now = Instant.now();
        Instant since = now.minus(days, ChronoUnit.DAYS);

        // Fetch logs from last X days
        List<AuditLog> logs = auditLogRepo.findByTimestampBetween(since, now);

        long[] buckets = new long[24]; // 0..23 hours

        for (AuditLog log : logs) {
            Instant ts = log.getTimestamp();
            if (ts == null)
                continue;

            int hour = ts.atZone(ANALYTICS_ZONE).getHour();
            buckets[hour]++;
        }

        // Build response
        Map<String, Long> histogram = new LinkedHashMap<>();
        for (int h = 0; h < 24; h++) {
            histogram.put(String.format("%02d:00", h), buckets[h]);
        }

        return Map.of(
                "windowDays", days,
                "histogram", histogram);
    }

    // ============================================================
    // C. TRENDING EVENTS (Composite score = likes + joins)
    // ============================================================

    @GetMapping("/trending-events")
    public List<Map<String, Object>> trendingEvents(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "20") int limit) {

        Instant now = Instant.now();
        Instant since = now.minus(days, ChronoUnit.DAYS);

        List<Event> allEvents = eventRepo.findAll();

        List<Map<String, Object>> scores = new ArrayList<>();

        for (Event e : allEvents) {
            String eventId = e.getId();

            // Count likes in last X days
            long likeCount = auditLogRepo.countByActionAndEventIdAndTimestampBetween(
                    AuditAction.EVENT_LIKED,
                    eventId,
                    since,
                    now);

            // Count join approvals in last X days
            long joinCount = auditLogRepo.countByActionAndEventIdAndTimestampBetween(
                    AuditAction.EVENT_JOIN_APPROVED,
                    eventId,
                    since,
                    now);

            long score = likeCount + joinCount;

            if (score > 0) {
                scores.add(
                        Map.of(
                                "eventId", eventId,
                                "title", e.getTitle(),
                                "score", score,
                                "likes", likeCount,
                                "joins", joinCount));
            }
        }

        // Sort by score DESC
        scores.sort((a, b) -> Long.compare(
                (Long) b.get("score"),
                (Long) a.get("score")));

        // Limit results
        return scores.stream()
                .limit(limit)
                .toList();
    }

    // ============================================================
    // D. TRENDING USERS (composite activity score)
    // ============================================================

    @GetMapping("/trending-users")
    public List<Map<String, Object>> trendingUsers(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "20") int limit) {

        Instant now = Instant.now();
        Instant since = now.minus(days, ChronoUnit.DAYS);

        List<User> allUsers = userRepo.findAll();

        List<Map<String, Object>> result = new ArrayList<>();

        for (User u : allUsers) {
            String userId = u.getId();

            // Events created in last X days
            long eventsCreated = eventRepo
                    .countByCreatedByUserIdAndCreatedAtBetween(userId, since, now);

            // Likes they gave in last X days
            long likesGiven = auditLogRepo
                    .countByActionAndPerformedByAndTimestampBetween(
                            AuditAction.EVENT_LIKED,
                            userId,
                            since,
                            now);

            // Joins they made in last X days
            long joins = auditLogRepo
                    .countByActionAndPerformedByAndTimestampBetween(
                            AuditAction.EVENT_JOIN_APPROVED,
                            userId,
                            since,
                            now);

            long score = eventsCreated + likesGiven + joins;

            if (score > 0) {
                result.add(
                        Map.of(
                                "userId", userId,
                                "name", Optional.ofNullable(u.getDisplayName())
                                        .or(() -> Optional.ofNullable(u.getName()))
                                        .orElse("Unknown"),
                                "score", score,
                                "eventsCreated", eventsCreated,
                                "likesGiven", likesGiven,
                                "joins", joins));
            }
        }

        // Sort: highest score first
        result.sort((a, b) -> Long.compare(
                (Long) b.get("score"),
                (Long) a.get("score")));

        return result.stream().limit(limit).toList();
    }

}
