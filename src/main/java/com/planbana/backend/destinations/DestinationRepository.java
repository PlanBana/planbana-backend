package com.planbana.backend.destinations;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface DestinationRepository extends MongoRepository<Destination, String> {
}
