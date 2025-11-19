package com.planbana.backend.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AuditLogRepository extends MongoRepository<AuditLog, String> {

    Page<AuditLog> findByCategory(AuditCategory category, Pageable pageable);

    Page<AuditLog> findByPerformedBy(String userId, Pageable pageable);

    Page<AuditLog> findByEventId(String eventId, Pageable pageable);

    Page<AuditLog> findByTargetUser(String targetUserId, Pageable pageable);

    // For your existing endpoint: /events/{id}/audit-logs
    List<AuditLog> findByEventIdOrderByTimestampDesc(String eventId);
}
