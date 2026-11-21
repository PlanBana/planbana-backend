package com.planbana.backend.admin.audit;

import com.planbana.backend.audit.AuditAction;
import com.planbana.backend.audit.AuditCategory;
import com.planbana.backend.audit.AuditLog;
import com.planbana.backend.audit.AuditLogRepository;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/audit")
public class AdminAuditLogController {

    private final AuditLogRepository auditRepo;

    public AdminAuditLogController(AuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    // ============================================================
    // 1. GET ALL LOGS (paged + sortable)
    // ============================================================
    @GetMapping
    public Page<AuditLog> getAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(200) int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        return auditRepo.findAll(pageable);
    }

    // ============================================================
    // 2. FILTER BY CATEGORY (enum)
    // ============================================================
    @GetMapping("/category")
    public Page<AuditLog> getByCategory(
            @RequestParam AuditCategory category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(200) int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return auditRepo.findByCategory(category, pageable);
    }

    // ============================================================
    // 3. FILTER BY ACTION (enum) — proper repository support
    // ============================================================

    @GetMapping("/action")
    public Page<AuditLog> getByAction(
            @RequestParam AuditAction action,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(200) int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));

        return auditRepo.findByAction(action, pageable);
    }

    // ============================================================
    // 4. FILTER BY EVENT ID
    // ============================================================

    @GetMapping("/event/{eventId}")
    public Page<AuditLog> getByEvent(
            @PathVariable String eventId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(200) int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));

        return auditRepo.findByEventId(eventId, pageable);
    }

    // ============================================================
    // 5. FILTER BY PERFORMED-BY USER ID (admin/moderator)
    // ============================================================

    @GetMapping("/performed-by/{userId}")
    public Page<AuditLog> getByPerformedBy(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(200) int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));

        return auditRepo.findByPerformedBy(userId, pageable);
    }
}
