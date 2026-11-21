package com.planbana.backend.chat;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface ChatRoomRepository extends MongoRepository<ChatRoom, String> {
    Optional<ChatRoom> findByEventId(String eventId);
}
