package com.planbana.backend.admin.user;

import com.planbana.backend.audit.AuditAction;
import com.planbana.backend.audit.AuditCategory;
import com.planbana.backend.audit.AuditLogger;
import com.planbana.backend.audit.AuditMetaBuilder;
import com.planbana.backend.user.User;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.planbana.backend.user.UserRepository;

import java.util.stream.Collectors;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService service;
    private final AuditLogger auditLogger;
    private final UserRepository userRepo; // add

    public AdminUserController(AdminUserService service, AuditLogger auditLogger, UserRepository userRepo) {
        this.service = service;
        this.auditLogger = auditLogger;
        this.userRepo = userRepo;
    }

    // ===================================================================
    // DTOs
    // ===================================================================

    public static class UserSummary {
        public String id;
        public String phone;
        public String email;
        public String name;
        public String displayName;
        public Set<String> roles;
        public Boolean disabled;
        public User.VerificationStatus verificationStatus;
        public Instant createdAt;

        public static UserSummary from(User u) {
            UserSummary s = new UserSummary();
            s.id = u.getId();
            s.phone = u.getPhone();
            s.email = u.getEmail();
            s.name = u.getName();
            s.displayName = u.getDisplayName();
            s.roles = u.getRoles();
            s.disabled = u.getDisabled();
            s.verificationStatus = u.getGovIdVerificationStatus();
            s.createdAt = u.getCreatedAt();
            return s;
        }
    }

    public static class UserDetail extends UserSummary {
        public String avatarUrl;
        public String bio;
        public List<String> languages;
        public Set<String> hobbies;
        public String gender;
        public java.time.LocalDate birthDate;

        public static UserDetail from(User u) {
            UserDetail d = new UserDetail();
            UserSummary base = UserSummary.from(u);

            d.id = base.id;
            d.phone = base.phone;
            d.email = base.email;
            d.name = base.name;
            d.displayName = base.displayName;
            d.roles = base.roles;
            d.disabled = base.disabled;
            d.verificationStatus = base.verificationStatus;
            d.createdAt = base.createdAt;

            d.avatarUrl = u.getAvatarUrl();
            d.bio = u.getBio();
            d.languages = u.getLanguages();
            d.hobbies = u.getHobbies();
            d.gender = u.getGender();
            d.birthDate = u.getBirthDate();

            return d;
        }
    }

    public static class UpdateRolesRequest {
        public Set<String> roles;
    }

    public static class UpdateVerificationRequest {
        public User.VerificationStatus status;
    }

    public static class SetDisabledRequest {
        public boolean disabled;
    }

    // ===================================================================
    // NEW ENDPOINT — Get ADMIN users only
    // ===================================================================

    @GetMapping("/admins")
    public List<UserSummary> listAdmins() {
        return service.getAllUsers().stream()
                .filter(u -> u.getRoles() != null && u.getRoles().contains("ADMIN"))
                .map(UserSummary::from)
                .collect(Collectors.toList());
    }

    // ===================================================================
    // Existing Endpoints
    // ===================================================================

    @GetMapping
    public Page<UserSummary> listUsers(
            @RequestParam(required = false, name = "search") String search,
            @RequestParam(required = false, name = "q") String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            Authentication auth) {

        String term = (search != null && !search.isBlank())
                ? search
                : (q != null && !q.isBlank() ? q : null);

        User.VerificationStatus vs = null;
        if (status != null && !status.isBlank()) {
            try {
                vs = User.VerificationStatus.valueOf(status);
            } catch (Exception ignored) {
            }
        }

        String adminId = auth.getName();

        Map<String, Object> auditData = new HashMap<>();
        auditData.put("search", term);
        auditData.put("roleFilter", role);
        auditData.put("verificationFilter", vs);
        auditData.put("page", page);
        auditData.put("size", size);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_VIEW_USER_LIST,
                adminId,
                null,
                null,
                auditData);

        Page<User> results = service.searchUsers(term, page, size, role, vs);
        return results.map(UserSummary::from);
    }

    @GetMapping("/{id}")
    public UserDetail getUser(@PathVariable String id, Authentication auth) {
        String adminId = auth.getName();
        User u = service.getUser(id);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_VIEW_USER_DETAIL,
                adminId,
                u.getId(),
                null,
                AuditMetaBuilder.create()
                        .put("email", u.getEmail())
                        .put("phone", u.getPhone())
                        .build());

        return UserDetail.from(u);
    }

    @PatchMapping("/{id}/roles")
    public UserDetail updateRoles(@PathVariable String id,
            @RequestBody UpdateRolesRequest req,
            Authentication auth) {

        String adminId = auth.getName();
        User before = service.getUser(id);

        User updated = service.updateRoles(id, req.roles, adminId);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_UPDATED_USER_ROLES,
                adminId,
                id,
                null,
                Map.of("oldRoles", before.getRoles(), "newRoles", updated.getRoles()));

        return UserDetail.from(updated);
    }

    @PatchMapping("/{id}/verification")
    public UserDetail updateVerification(@PathVariable String id,
            @RequestBody UpdateVerificationRequest req,
            Authentication auth) {

        String adminId = auth.getName();
        User before = service.getUser(id);

        User updated = service.updateVerificationStatus(id, req.status, null);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                AuditAction.ADMIN_UPDATED_VERIFICATION_STATUS,
                adminId,
                id,
                null,
                Map.of("oldStatus", before.getGovIdVerificationStatus(),
                        "newStatus", updated.getGovIdVerificationStatus()));

        return UserDetail.from(updated);
    }

    @PatchMapping("/{id}/disabled")
    public UserDetail setDisabled(@PathVariable String id,
                                  @RequestBody SetDisabledRequest req,
                                  Authentication auth) {

        String adminId = auth.getName();
        User before = service.getUser(id);

        // Pass null as the reason for now
        User updated = service.setDisabled(id, req.disabled, null);

        // 🔥 THIS is the missing piece: invalidate ALL existing sessions
        updated.incrementTokenVersion();

        // optional nice metadata (since you already have these fields)
        if (req.disabled) {
            updated.setDisabledAt(Instant.now());
            if (updated.getDisabledReason() == null) {
                updated.setDisabledReason("Blocked by admin");
            }
        } else {
            updated.setDisabledAt(null);
            updated.setDisabledReason(null);
        }

        // ✅ persist version bump
        updated = userRepo.save(updated);

        auditLogger.log(
                AuditCategory.ADMIN_USERS,
                req.disabled ? AuditAction.ADMIN_DISABLED_USER : AuditAction.ADMIN_ENABLED_USER,
                adminId,
                id,
                null,
                Map.of("oldDisabled", before.getDisabled(),
                        "newDisabled", req.disabled,
                        "tokenVersion", updated.getTokenVersion()));

        return UserDetail.from(updated);
    }

    @DeleteMapping("/{id}")
        public void deleteUser(@PathVariable String id, Authentication auth) {

        String adminId = auth.getName();
        User toDelete = service.getUser(id);

        service.deleteUserHard(id, adminId);

        auditLogger.log(
            AuditCategory.ADMIN_USERS,
            AuditAction.ADMIN_DELETED_USER,
            adminId,
            id,
            null,
            Map.of("email", toDelete.getEmail(),
                "phone", toDelete.getPhone(),
                "roles", toDelete.getRoles()));
        }
}
