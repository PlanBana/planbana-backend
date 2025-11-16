package com.planbana.backend.admin;

import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import com.planbana.backend.events.Event;
import com.planbana.backend.events.EventRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminStatsController {

    private final UserRepository users;
    private final EventRepository events;

    public AdminStatsController(UserRepository users, EventRepository events) {
        this.users = users;
        this.events = events;
    }

    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        long totalUsers = users.count();
        long verifiedUsers = users.countByGovIdVerificationStatus(User.VerificationStatus.VERIFIED);
        long pendingVerifications = users.countByGovIdVerificationStatus(User.VerificationStatus.PENDING);

        long totalEvents = events.count();
        long upcomingEvents = events.countByStartAtAfter(Instant.now());
        long activeHosts = events.countDistinctHostId();

        return Map.of(
                "totalUsers", totalUsers,
                "verifiedUsers", verifiedUsers,
                "pendingVerifications", pendingVerifications,
                "totalEvents", totalEvents,
                "upcomingEvents", upcomingEvents,
                "activeHosts", activeHosts);
    }
}
