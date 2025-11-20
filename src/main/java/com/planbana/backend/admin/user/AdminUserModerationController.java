package com.planbana.backend.admin.user;

import com.planbana.backend.audit.*;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/admin/users/moderation")
public class AdminUserModerationController {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final AuditLogger auditLogger;

    public AdminUserModerationController(
            UserRepository userRepo,
            PasswordEncoder encoder,
            AuditLogger auditLogger) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.auditLogger = auditLogger;
    }

    // =========================================================================
    // 1️⃣ Get Full User Details (Admin Only)
    // =========================================================================
    @GetMapping("/{id}")
    public User getUser(@PathVariable String id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    // =========================================================================
    // 2️⃣ Ban / Disable User (with reason)
    // =========================================================================
    public static class BanRequest {
        @NotBlank
        public String reason;
    }

    @PostMapping("/{id}/ban")
    public Map<String, Object> banUser(
            @PathVariable String id,
            @RequestBody BanRequest req,
            @RequestHeader("admin-id") String adminId) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        user.setDisabled(true);
        user.setDisabledReason(req.reason);
        user.setDisabledAt(Instant.now());
        userRepo.save(user);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_DISABLED_USER,
                adminId,
                user.getId(),
                null,
                Map.of("reason", req.reason));

        return Map.of("status", "BANNED", "reason", req.reason);
    }

    // =========================================================================
    // 3️⃣ Unban / Enable User
    // =========================================================================
    @PostMapping("/{id}/unban")
    public Map<String, Object> unbanUser(
            @PathVariable String id,
            @RequestHeader("admin-id") String adminId) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        user.setDisabled(false);
        user.setDisabledReason(null);
        user.setDisabledAt(null);
        userRepo.save(user);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_ENABLED_USER,
                adminId,
                user.getId(),
                null,
                Map.of("message", "User reactivated"));

        return Map.of("status", "UNBANNED");
    }

    // =========================================================================
    // 4️⃣ Admin Notes (add notes to user)
    // =========================================================================
    public static class NoteRequest {
        @NotBlank
        public String note;
    }

    @PostMapping("/{id}/notes")
    public Map<String, Object> addAdminNote(
            @PathVariable String id,
            @RequestBody NoteRequest req,
            @RequestHeader("admin-id") String adminId) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        User.AdminNote note = new User.AdminNote();
        note.id = UUID.randomUUID().toString();
        note.adminId = adminId;
        note.note = req.note;

        user.getAdminNotes().add(note);
        userRepo.save(user);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_UPDATED_USER_PROFILE,
                adminId,
                user.getId(),
                null,
                Map.of("note", req.note));

        return Map.of("noteId", note.id, "createdAt", note.createdAt);
    }

    // =========================================================================
    // 5️⃣ View User Notes
    // =========================================================================
    @GetMapping("/{id}/notes")
    public List<User.AdminNote> listNotes(@PathVariable String id) {
        return userRepo.findById(id)
                .map(User::getAdminNotes)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // =========================================================================
    // 6️⃣ Update Roles (e.g., ADMIN / USER)
    // =========================================================================
    public static class RolesRequest {
        public Set<String> roles;
    }

    @PostMapping("/{id}/roles")
    public Map<String, Object> updateRoles(
            @PathVariable String id,
            @RequestBody RolesRequest req,
            @RequestHeader("admin-id") String adminId) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        user.setRoles(req.roles);
        userRepo.save(user);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_UPDATED_USER_ROLES,
                adminId,
                user.getId(),
                null,
                Map.of("roles", req.roles));

        return Map.of("roles", req.roles);
    }

    // =========================================================================
    // 7️⃣ Admin Reset Password
    // =========================================================================
    public static class ResetPasswordRequest {
        @NotBlank
        public String newPassword;
    }

    @PostMapping("/{id}/reset-password")
    public Map<String, String> resetPassword(
            @PathVariable String id,
            @RequestBody ResetPasswordRequest req,
            @RequestHeader("admin-id") String adminId) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        user.setPasswordHash(encoder.encode(req.newPassword));
        userRepo.save(user);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_RESET_USER_PASSWORD,
                adminId,
                user.getId(),
                null,
                Map.of("message", "Password reset by admin"));

        return Map.of("message", "Password updated");
    }
}
