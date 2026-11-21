package com.planbana.backend.admin.user;

import com.planbana.backend.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/users/actions")
public class AdminUserActionController {

    private final AdminUserActionService service;

    public AdminUserActionController(AdminUserActionService service) {
        this.service = service;
    }

    private String admin(Authentication auth) {
        return auth.getName(); // phone as admin ID
    }

    // ============================================================
    // 1. Reset Password
    // ============================================================

    @PostMapping("/{id}/reset-password")
    public Map<String, Object> resetPassword(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        User u = service.resetPassword(id, body.get("newPassword"), admin(auth));
        return Map.of("message", "Password reset", "userId", u.getId());
    }

    // ============================================================
    // 2. Update Roles
    // ============================================================

    @PatchMapping("/{id}/roles")
    public User updateRoles(
            @PathVariable String id,
            @RequestBody Map<String, Set<String>> body,
            Authentication auth) {
        return service.updateRoles(id, body.get("roles"), admin(auth));
    }

    // ============================================================
    // 3. Update Profile Fields
    // ============================================================

    @PatchMapping("/{id}/profile")
    public User updateProfile(
            @PathVariable String id,
            @RequestBody AdminUserUpdateProfileRequest dto,
            Authentication auth) {
        return service.updateProfile(id, dto, admin(auth));
    }

    // ============================================================
    // 4. Force Verify / Reject Verification
    // ============================================================

    @PatchMapping("/{id}/verification")
    public User verifyUser(
            @PathVariable String id,
            @RequestBody Map<String, User.VerificationStatus> body,
            Authentication auth) {
        return service.forceVerify(id, body.get("status"), admin(auth));
    }

    // ============================================================
    // 5. Enable / Disable User
    // ============================================================

    @PatchMapping("/{id}/disabled")
    public User disable(
            @PathVariable String id,
            @RequestBody Map<String, Boolean> body,
            Authentication auth) {
        return service.setDisabled(id, body.get("disabled"), admin(auth));
    }
}
