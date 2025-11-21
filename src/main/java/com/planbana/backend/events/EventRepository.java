package com.planbana.backend.events;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EventRepository extends MongoRepository<Event, String> {

    List<Event> findByCreatedByUserId(String createdByUserId);

    long countByStartAtAfter(Instant moment);

    long countByCreatedAtAfter(Instant date);

    long countByEndAtBefore(Instant date);

    boolean existsById(String id);

    long countByStatus(Event.Status status);

    long countByCreatedAtBetween(Instant start, Instant end);

    // used in /top-users endpoint
    long countByCreatedByUserId(String userId);

    Page<Event> findByStatus(Event.Status status, Pageable pageable);

    // ❌ REMOVED — invalid field path
    // long countByIdAndParticipants_JoinedAtBetween(String eventId, Instant start,
    // Instant end);

    // admin analytics - events created by user in time window
    long countByCreatedByUserIdAndCreatedAtBetween(
            String userId,
            Instant start,
            Instant end);
}
