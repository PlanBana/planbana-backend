package com.planbana.backend.admin;

import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/verifications")
public class VerificationAdminController {

    private final UserRepository users;

    public VerificationAdminController(UserRepository users) {
        this.users = users;
    }

    // List all pending verifications
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @GetMapping("/pending")
    public List<Map<String, Object>> pending() {
        return users.findAllByGovIdVerificationStatus(User.VerificationStatus.PENDING)
                .stream()
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "phone", u.getPhone(),
                        "displayName", u.getDisplayName(),
                        "status", u.getGovIdVerificationStatus().name()))
                .toList();
    }

    // Approve or reject
    public static class Decision {
        public String status;
    }

    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @PostMapping("/{userId}/decision")
    public Map<String, String> decide(@PathVariable String userId, @RequestBody Decision body) {
        User u = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!"VERIFIED".equals(body.status) && !"REJECTED".equals(body.status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be VERIFIED or REJECTED");
        }

        u.setGovIdVerificationStatus(User.VerificationStatus.valueOf(body.status));
        users.save(u);

        return Map.of("userId", u.getId(), "status", u.getGovIdVerificationStatus().name());
    }
}
