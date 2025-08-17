package com.planbana.backend.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MessageRepository extends MongoRepository<Message, String> {
  Page<Message> findByConversationIdOrderByCreatedAtAsc(String conversationId, Pageable pageable);
}
