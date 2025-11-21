package com.planbana.backend.admin.user;

import com.planbana.backend.audit.AuditLogger;
import com.planbana.backend.audit.AuditAction;
import com.planbana.backend.audit.AuditCategory;
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
    private final AuditLogger audit;

    public AdminUserActionService(UserRepository userRepo, PasswordEncoder encoder, AuditLogger audit) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.audit = audit;
    }

    private User get(String id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    // 1. Reset password
    public User resetPassword(String id, String newPassword, String adminPhone) {
        User u = get(id);
        u.setPasswordHash(encoder.encode(newPassword));
        userRepo.save(u);

        audit.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_RESET_PASSWORD,
                adminPhone,
                id,
                null);

        return u;
    }

    // 2. Update roles
    public User updateRoles(String id, Set<String> roles, String adminPhone) {
        User u = get(id);
        u.setRoles(roles);
        userRepo.save(u);

        audit.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_UPDATED_ROLES,
                adminPhone,
                id,
                null);

        return u;
    }

    // 3. Update profile
    public User updateProfile(String id, AdminUserUpdateProfileRequest dto, String adminPhone) {
        User u = get(id);

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

        audit.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_UPDATED_PROFILE,
                adminPhone,
                id,
                null);

        return u;
    }

    // 4. Force verification
    public User forceVerify(String id, User.VerificationStatus status, String adminPhone) {
        User u = get(id);
        u.setGovIdVerificationStatus(status);
        userRepo.save(u);

        audit.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.valueOf("ADMIN_FORCE_VERIFICATION_" + status),
                adminPhone,
                id,
                null);

        return u;
    }

    // 5. Enable/disable user
    public User setDisabled(String id, boolean disabled, String adminPhone) {
        User u = get(id);
        u.setDisabled(disabled);
        userRepo.save(u);

        audit.log(
                AuditCategory.ADMIN_USERS,
                disabled ? AuditAction.ADMIN_DISABLED_USER : AuditAction.ADMIN_ENABLED_USER,
                adminPhone,
                id,
                null);

        return u;
    }
}
