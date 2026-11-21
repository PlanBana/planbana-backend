package com.planbana.backend.admin.kyc;

import com.planbana.backend.audit.AuditAction;
import com.planbana.backend.audit.AuditCategory;
import com.planbana.backend.audit.AuditLogger;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/admin/kyc")
public class AdminKycController {

    private final UserRepository userRepo;
    private final AuditLogger auditLogger;

    public AdminKycController(UserRepository userRepo, AuditLogger auditLogger) {
        this.userRepo = userRepo;
        this.auditLogger = auditLogger;
    }

    // ============================================================
    // 1️⃣ LIST ALL PENDING KYC REQUESTS
    // ============================================================
    @GetMapping("/pending")
    public List<User> getPendingUsers() {
        return userRepo.findAllByGovIdVerificationStatus(User.VerificationStatus.PENDING);
    }

    // ============================================================
    // 2️⃣ GET SINGLE USER KYC INFO
    // ============================================================
    @GetMapping("/{userId}")
    public User getUserKyc(@PathVariable String userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    // ============================================================
    // 3️⃣ APPROVE / VERIFY KYC
    // ============================================================
    @PostMapping("/{userId}/approve")
    public Map<String, String> approveKyc(
            @PathVariable String userId,
            @RequestParam String adminId) {

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        User.VerificationStatus old = user.getGovIdVerificationStatus();
        user.setGovIdVerificationStatus(User.VerificationStatus.VERIFIED);
        userRepo.save(user);

        auditLogger.log(
                AuditCategory.USER_ACCOUNT,
                AuditAction.USER_GOV_ID_VERIFIED,
                adminId,
                user.getId(),
                null,
                Map.of("oldStatus", old.name(), "newStatus", "VERIFIED"));

        return Map.of("status", "VERIFIED");
    }

    // ============================================================
    // 4️⃣ REJECT KYC (ADMIN)
    // ============================================================

    public static class RejectReason {
        @NotBlank
        public String reason;
    }

    @PostMapping("/{userId}/reject")
    public Map<String, String> rejectKyc(
            @PathVariable String userId,
            @RequestParam String adminId,
            @RequestBody RejectReason req) {

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        User.VerificationStatus old = user.getGovIdVerificationStatus();
        user.setGovIdVerificationStatus(User.VerificationStatus.REJECTED);
        userRepo.save(user);

        auditLogger.log(
                AuditCategory.USER_ACCOUNT,
                AuditAction.USER_GOV_ID_REJECTED,
                adminId,
                user.getId(),
                null,
                Map.of(
                        "oldStatus", old.name(),
                        "newStatus", "REJECTED",
                        "reason", req.reason));

        return Map.of("status", "REJECTED");
    }
}
