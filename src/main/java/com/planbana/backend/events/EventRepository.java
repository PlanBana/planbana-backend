package com.planbana.backend.events;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface EventRepository extends MongoRepository<Event, String> {
    List<Event> findByCreatedByUserId(String createdByUserId);
}
