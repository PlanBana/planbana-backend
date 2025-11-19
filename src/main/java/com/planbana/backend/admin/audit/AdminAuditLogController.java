package com.planbana.backend.admin.audit;

import com.planbana.backend.events.audit.AuditLog;
import com.planbana.backend.events.audit.AuditLogRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/audit")
public class AdminAuditLogController {

    private final AuditLogRepository auditRepo;

    public AdminAuditLogController(AuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    // ==============================================================
    // 1. GET ALL AUDIT LOGS (paged)
    // ==============================================================

    @GetMapping
    public Page<AuditLog> getAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(200) int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        return auditRepo.findAll(pageable);
    }

    // ==============================================================
    // 2. GET AUDIT LOGS FOR A SINGLE EVENT
    // ==============================================================

    @GetMapping("/event/{eventId}")
    public Map<String, Object> getByEvent(@PathVariable String eventId) {
        return Map.of(
                "eventId", eventId,
                "logs", auditRepo.findByEventIdOrderByTimestampDesc(eventId));
    }

    // ==============================================================
    // 3. SEARCH AUDIT LOGS BY ACTION TYPE
    // ==============================================================

    @GetMapping("/action")
    public Page<AuditLog> getByAction(
            @RequestParam String action,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));

        // Mongo regex search
        return auditRepo.findByActionRegexIgnoreCase(action, pageable);
    }

    // ==============================================================
    // 4. GET AUDIT LOGS PERFORMED BY A SPECIFIC ADMIN
    // ==============================================================

    @GetMapping("/performed-by/{adminId}")
    public Page<AuditLog> getByAdmin(
            @PathVariable String adminId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));

        return auditRepo.findByPerformedBy(adminId, pageable);
    }
}
