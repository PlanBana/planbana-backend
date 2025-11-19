package com.planbana.backend.admin.user;

import com.planbana.backend.audit.AuditAction;
import com.planbana.backend.audit.AuditCategory;
import com.planbana.backend.audit.AuditLogger;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

@Service
public class AdminUserService {

    private final UserRepository userRepo;
    private final AuditLogger auditLogger;

    public AdminUserService(UserRepository userRepo, AuditLogger auditLogger) {
        this.userRepo = userRepo;
        this.auditLogger = auditLogger;
    }

    // ============================================================
    // Search Users (Admin)
    // ============================================================

    public Page<User> searchUsers(
            String search,
            int page,
            int size,
            String role,
            User.VerificationStatus status) {

        Pageable pageable = PageRequest.of(
                page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        return (search != null && !search.isBlank())
                ? userRepo.searchAdmin(search.trim(), pageable, role, status)
                : userRepo.findAllAdmin(pageable, role, status);
    }

    // ============================================================
    // Get Single User
    // ============================================================

    public User getUser(String id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    // ============================================================
    // Update User Roles
    // ============================================================

    public User updateRoles(String id, Set<String> roles) {

        if (roles == null || roles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "User must have at least one role");
        }

        User before = getUser(id);
        User after = before;

        after.setRoles(roles);
        after = userRepo.save(after);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_UPDATED_USER_ROLES,
                null, // admin injected at controller level (auth)
                id,
                null,
                Map.of(
                        "oldRoles", before.getRoles(),
                        "newRoles", roles));

        return after;
    }

    // ============================================================
    // Update Verification Status (KYC)
    // ============================================================

    public User updateVerificationStatus(String id, User.VerificationStatus status) {

        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Verification status cannot be null");
        }

        User before = getUser(id);
        User after = before;

        after.setGovIdVerificationStatus(status);
        after = userRepo.save(after);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_UPDATED_VERIFICATION_STATUS,
                null,
                id,
                null,
                Map.of(
                        "oldStatus", before.getGovIdVerificationStatus(),
                        "newStatus", status));

        return after;
    }

    // ============================================================
    // Enable / Disable User
    // ============================================================

    public User setDisabled(String id, boolean disabled) {

        User before = getUser(id);
        User after = before;

        after.setDisabled(disabled);
        after = userRepo.save(after);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                disabled ? AuditAction.ADMIN_DISABLED_USER : AuditAction.ADMIN_ENABLED_USER,
                null,
                id,
                null,
                Map.of(
                        "oldDisabled", before.getDisabled(),
                        "newDisabled", disabled));

        return after;
    }

    // ============================================================
    // Hard Delete User
    // ============================================================

    public void deleteUserHard(String id) {

        User u = getUser(id);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_DELETED_USER,
                null,
                id,
                null,
                Map.of(
                        "email", u.getEmail(),
                        "phone", u.getPhone(),
                        "roles", u.getRoles()));

        userRepo.deleteById(id);
    }

    // ============================================================
    // (Optional) Admin Create User
    // ============================================================

    public User createUserAsAdmin(User u, String adminId) {

        User saved = userRepo.save(u);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_CREATED_USER,
                adminId,
                saved.getId(),
                null,
                Map.of(
                        "phone", saved.getPhone(),
                        "email", saved.getEmail(),
                        "roles", saved.getRoles()));

        return saved;
    }

    // ============================================================
    // (Optional) Admin Reset Password
    // ============================================================

    public User resetPasswordAsAdmin(String userId, String newHash, String adminId) {

        User target = getUser(userId);

        target.setPasswordHash(newHash);
        userRepo.save(target);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_RESET_USER_PASSWORD,
                adminId,
                userId,
                null,
                Map.of("dummy", "password_reset"));

        return target;
    }
}
