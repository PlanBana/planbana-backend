package com.planbana.backend.events.share;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ShareLinkRepository extends MongoRepository<ShareLink, String> {
  Optional<ShareLink> findByCodeHash(String codeHash);
  List<ShareLink> findByEventIdAndCreatedByUserId(String eventId, String createdByUserId);
}
