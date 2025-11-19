package com.planbana.backend.events;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface EventRepository extends MongoRepository<Event, String> {
    // List<Event> findByCreatedByUserId(String createdByUserId);

    // long countByStartAtAfter(Instant moment);

    // @Query(value = "{}", count = true) // adjust for Mongo/JPA style
    // long countDistinctHostId();

    // long countByCreatedAtAfter(Instant date);

    // long countByEndAtBefore(Instant date);

    // boolean existsById(String id);

    // Page<Event> findByStatus(Event.Status status, Pageable pageable);

    List<Event> findByCreatedByUserId(String createdByUserId);

    long countByStartAtAfter(Instant moment);

    long countByCreatedAtAfter(Instant date);

    long countByEndAtBefore(Instant date);

    boolean existsById(String id);

    long countByStatus(Event.Status status);

    Page<Event> findByStatus(Event.Status status, Pageable pageable);

}
