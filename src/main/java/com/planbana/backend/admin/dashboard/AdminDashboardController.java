package com.planbana.backend.admin.dashboard;

import com.planbana.backend.events.Event;
import com.planbana.backend.events.EventRepository;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final UserRepository userRepo;
    private final EventRepository eventRepo;

    public AdminDashboardController(UserRepository userRepo, EventRepository eventRepo) {
        this.userRepo = userRepo;
        this.eventRepo = eventRepo;
    }

    // ============================================================
    // Dashboard Overview Summary
    // ============================================================

    @GetMapping("/overview")
    public Map<String, Object> overview() {

        long totalUsers = userRepo.count();
        long verifiedUsers = userRepo.countByGovIdVerificationStatus(User.VerificationStatus.VERIFIED);
        long pendingVerification = userRepo.countByGovIdVerificationStatus(User.VerificationStatus.PENDING);
        long disabledUsers = userRepo.countByDisabled(true);

        long totalEvents = eventRepo.count();
        long activeEvents = eventRepo.countByStatus(Event.Status.ACTIVE);
        long blockedEvents = eventRepo.countByStatus(Event.Status.BLOCKED);
        long pendingReviewEvents = eventRepo.countByStatus(Event.Status.PENDING_REVIEW);

        Instant now = Instant.now();
        Instant last7Days = now.minus(7, ChronoUnit.DAYS);
        Instant last30Days = now.minus(30, ChronoUnit.DAYS);

        long eventsCreatedLast7Days = eventRepo.countByCreatedAtAfter(last7Days);
        long eventsCreatedLast30Days = eventRepo.countByCreatedAtAfter(last30Days);

        long usersJoinedLast7Days = userRepo.countByCreatedAtAfter(last7Days);
        long usersJoinedLast30Days = userRepo.countByCreatedAtAfter(last30Days);

        long upcomingEventsNext7Days = eventRepo.countByStartAtAfter(now);
        long pastEventsLast30Days = eventRepo.countByEndAtBefore(now);

        return Map.of(
                "users", Map.of(
                        "total", totalUsers,
                        "verified", verifiedUsers,
                        "pendingVerification", pendingVerification,
                        "disabled", disabledUsers,
                        "joinedLast7Days", usersJoinedLast7Days,
                        "joinedLast30Days", usersJoinedLast30Days),
                "events", Map.of(
                        "total", totalEvents,
                        "active", activeEvents,
                        "blocked", blockedEvents,
                        "pendingReview", pendingReviewEvents,
                        "createdLast7Days", eventsCreatedLast7Days,
                        "createdLast30Days", eventsCreatedLast30Days,
                        "upcomingNext7Days", upcomingEventsNext7Days,
                        "pastLast30Days", pastEventsLast30Days));
    }

    // ============================================================
    // Event Status Distribution (Pie Chart)
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
    // User Verification Breakdown (Pie Chart)
    // ============================================================

    @GetMapping("/user-verification-breakdown")
    public Map<String, Long> userVerificationBreakdown() {
        return Map.of(
                "verified", userRepo.countByGovIdVerificationStatus(User.VerificationStatus.VERIFIED),
                "pending", userRepo.countByGovIdVerificationStatus(User.VerificationStatus.PENDING),
                "rejected", userRepo.countByGovIdVerificationStatus(User.VerificationStatus.REJECTED));
    }

    // ============================================================
    // Daily User Signups & Event Creations (Line Chart)
    // ============================================================

    @GetMapping("/activity-stats")
    public Map<String, Object> activityStats() {

        Instant today = Instant.now();
        Instant last30 = today.minus(30, ChronoUnit.DAYS);

        long usersLast30 = userRepo.countByCreatedAtAfter(last30);
        long eventsLast30 = eventRepo.countByCreatedAtAfter(last30);

        return Map.of(
                "last30DaysUsers", usersLast30,
                "last30DaysEvents", eventsLast30);
    }
}
