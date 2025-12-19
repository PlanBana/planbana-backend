package com.planbana.backend.admin.user;

import com.planbana.backend.admin.ratelimit.AdminRateLimiter;
import com.planbana.backend.audit.AuditAction;
import com.planbana.backend.audit.AuditCategory;
import com.planbana.backend.audit.AuditLogger;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;

import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminUserService {

        private final UserRepository userRepo;
        private final AuditLogger auditLogger;
        private final AdminRateLimiter rateLimiter;

        public AdminUserService(UserRepository userRepo, AdminRateLimiter rateLimiter, AuditLogger auditLogger) {
                this.userRepo = userRepo;
                this.rateLimiter = rateLimiter;
                this.auditLogger = auditLogger;
        }

        // =========================================================================
        // New helper for listing admins
        // =========================================================================
        public List<User> getAllUsers() {
                return userRepo.findAll();
        }

        // =========================================================================
        // Search Users
        // =========================================================================
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

        // =========================================================================
        // Get Single User
        // =========================================================================
        public User getUser(String id) {
                return userRepo.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        }

        // =========================================================================
        // Update Roles
        // =========================================================================
        public User updateRoles(String id, Set<String> roles, String adminId) {

                rateLimiter.check(
                                adminId,
                                "UPDATE_ROLES",
                                10,
                                60);

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
                                null,
                                id,
                                null,
                                Map.of(
                                                "oldRoles", before.getRoles(),
                                                "newRoles", roles));

                return after;
        }

        // =========================================================================
        // Update Verification Status
        // =========================================================================
        public User updateVerificationStatus(String id, User.VerificationStatus status, String adminId) {

                if (status == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Verification status cannot be null");
                }

                rateLimiter.check(
                                adminId,
                                "KYC_REVIEW",
                                20,
                                60);

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

        // =========================================================================
        // Disable / Enable
        // =========================================================================
        public User setDisabled(String id, boolean disabled, String adminId) {

                User before = getUser(id);
                User after = before;

                after.setDisabled(disabled);
                after = userRepo.save(after);

                // 🛡️ Rate limit
                rateLimiter.check(
                                adminId,
                                "BLOCK_USER",
                                5, // max 5
                                60 // per 60 seconds
                );

                auditLogger.log(
                                AuditCategory.ADMIN_USERS,
                                disabled ? AuditAction.ADMIN_DISABLED_USER : AuditAction.ADMIN_ENABLED_USER,
                                null,
                                id,
                                null,
                                Map.of(
                                                "oldDisabled", before.getDisabled(),
                                                "newDisabled", disabled));

                // 🔥 FORCE LOGOUT FROM ALL DEVICES
                before.incrementTokenVersion();

                return after;
        }

        // =========================================================================
        // Hard Delete User
        // =========================================================================
        public void deleteUserHard(String id, String adminId) {

                User u = getUser(id);

                // 🛡️ Strict limit
                rateLimiter.check(
                                adminId,
                                "DELETE_USER",
                                2, // only 2
                                3600 // per hour
                );

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
}
