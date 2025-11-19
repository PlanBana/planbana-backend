package com.planbana.backend.admin.user;

import com.planbana.backend.events.audit.AuditLog;
import com.planbana.backend.events.audit.AuditLogRepository;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Service
public class AdminUserActionService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final AuditLogRepository auditRepo;

    public AdminUserActionService(UserRepository userRepo, PasswordEncoder encoder, AuditLogRepository auditRepo) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.auditRepo = auditRepo;
    }

    private User get(String id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void log(String targetUser, String action, String adminPhone) {
        auditRepo.save(new AuditLog(
                null, // no eventId
                action,
                adminPhone,
                targetUser));
    }

    // ============================================================
    // 1. Reset Password
    // ============================================================

    public User resetPassword(String userId, String newPassword, String adminPhone) {
        User u = get(userId);
        u.setPasswordHash(encoder.encode(newPassword));
        userRepo.save(u);

        log(userId, "ADMIN_RESET_PASSWORD", adminPhone);
        return u;
    }

    // ============================================================
    // 2. Update Roles
    // ============================================================

    public User updateRoles(String userId, Set<String> roles, String adminPhone) {
        if (roles == null || roles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User must have at least one role");
        }

        User u = get(userId);
        u.setRoles(roles);
        userRepo.save(u);

        log(userId, "ADMIN_UPDATED_ROLES", adminPhone);
        return u;
    }

    // ============================================================
    // 3. Update Profile Fields
    // ============================================================

    public User updateProfile(String userId, AdminUserUpdateProfileRequest dto, String adminPhone) {
        User u = get(userId);

        if (dto.name != null)
            u.setName(dto.name);
        if (dto.displayName != null)
            u.setDisplayName(dto.displayName);
        if (dto.avatarUrl != null)
            u.setAvatarUrl(dto.avatarUrl);
        if (dto.bio != null)
            u.setBio(dto.bio);
        if (dto.languages != null)
            u.setLanguages(dto.languages);
        if (dto.hobbies != null)
            u.setHobbies(dto.hobbies);
        if (dto.gender != null)
            u.setGender(dto.gender);
        if (dto.birthDate != null)
            u.setBirthDate(dto.birthDate);

        userRepo.save(u);

        log(userId, "ADMIN_UPDATED_PROFILE", adminPhone);
        return u;
    }

    // ============================================================
    // 4. Force Verify User
    // ============================================================

    public User forceVerify(String userId, User.VerificationStatus status, String adminPhone) {
        User u = get(userId);
        u.setGovIdVerificationStatus(status);
        userRepo.save(u);

        log(userId, "ADMIN_FORCE_VERIFICATION_" + status, adminPhone);
        return u;
    }

    // ============================================================
    // 5. Enable / Disable User
    // ============================================================

    public User setDisabled(String userId, boolean disabled, String adminPhone) {
        User u = get(userId);
        u.setDisabled(disabled);
        userRepo.save(u);

        log(userId, disabled ? "ADMIN_DISABLED_USER" : "ADMIN_ENABLED_USER", adminPhone);
        return u;
    }
}
