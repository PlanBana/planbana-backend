package com.planbana.backend.events.audit;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AuditLogRepository extends MongoRepository<AuditLog, String> {

    List<AuditLog> findByEventIdOrderByTimestampDesc(String eventId);
}
