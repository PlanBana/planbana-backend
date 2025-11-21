package com.planbana.backend.admin.user;

import com.planbana.backend.audit.*;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AdminUserModerationService {

    private final UserRepository repo;
    private final AuditLogger auditLogger;

    public AdminUserModerationService(UserRepository repo, AuditLogger auditLogger) {
        this.repo = repo;
        this.auditLogger = auditLogger;
    }

    public User getUserOrThrow(String userId) {
        return repo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    // ============================================================
    // 1️⃣ RESET PASSWORD (admin)
    // ============================================================
    public void resetPassword(String targetUserId, String adminId, String newPasswordHash) {
        User u = getUserOrThrow(targetUserId);

        u.setPasswordHash(newPasswordHash);
        repo.save(u);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_RESET_PASSWORD,
                adminId,
                targetUserId,
                null,
                Map.of("action", "password_reset"));
    }

    // ============================================================
    // 2️⃣ BAN USER
    // ============================================================
    public void banUser(String targetUserId, String adminId, String reason) {
        User u = getUserOrThrow(targetUserId);

        u.setBanned(true);
        u.setBannedReason(reason);
        u.setBannedAt(Instant.now());
        repo.save(u);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_USER_BANNED,
                adminId,
                targetUserId,
                null,
                Map.of("reason", reason));
    }

    // ============================================================
    // 3️⃣ UNBAN USER
    // ============================================================
    public void unbanUser(String targetUserId, String adminId) {
        User u = getUserOrThrow(targetUserId);

        u.setBanned(false);
        u.setBannedReason(null);
        u.setBannedAt(null);
        repo.save(u);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_USER_UNBANNED,
                adminId,
                targetUserId,
                null,
                Map.of());
    }

    // ============================================================
    // 4️⃣ ADD ADMIN NOTE
    // ============================================================
    public User addAdminNote(String userId, String adminId, String note) {
        User u = getUserOrThrow(userId);

        u.getAdminNotes().add(new User.AdminNote(adminId, note));
        repo.save(u);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_ADDED_NOTE,
                adminId,
                userId,
                null,
                Map.of("note", note));

        return u;
    }

    // ============================================================
    // 5️⃣ ASSIGN MODERATOR REGIONS / CATEGORIES
    // ============================================================
    public User updateModeratorRegions(String userId, String adminId, String[] regions) {
        User u = getUserOrThrow(userId);

        u.setModeratorRegions(List.of(regions));
        repo.save(u);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_UPDATED_MODERATOR_SCOPE,
                adminId,
                userId,
                null,
                Map.of("regions", regions));

        return u;
    }
}
