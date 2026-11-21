package com.planbana.backend.moderation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface EventReportRepository extends MongoRepository<EventReport, String> {

    boolean existsByEventIdAndReporterUserId(String eventId, String reporterUserId);

    long countByEventId(String eventId);

    List<EventReport> findByEventIdOrderByCreatedAtDesc(String eventId);

    // Moderation queue filters
    Page<EventReport> findByStatus(EventReport.Status status, Pageable pageable);

    Page<EventReport> findByCategory(String category, Pageable pageable);

    Page<EventReport> findByStatusAndCategory(EventReport.Status status, String category, Pageable pageable);

    // Search all (fallback)
    Page<EventReport> findAll(Pageable pageable);
}
