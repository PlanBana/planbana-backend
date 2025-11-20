package com.planbana.backend.events.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AuditLogRepository extends MongoRepository<AuditLog, String> {

    // Existing methods (keep)
    List<AuditLog> findByEventIdOrderByTimestampDesc(String eventId);

    Page<AuditLog> findByActionRegexIgnoreCase(String action, Pageable pageable);

    Page<AuditLog> findByPerformedBy(String performedBy, Pageable pageable);

    // ✅ NEW: for dashboard / admin activity panels
    List<AuditLog> findTop50ByCategoryOrderByTimestampDesc(String category);

    Page<AuditLog> findByCategoryOrderByTimestampDesc(String category, Pageable pageable);
}
