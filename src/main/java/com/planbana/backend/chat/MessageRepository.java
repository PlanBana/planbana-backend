package com.planbana.backend.chat;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface MessageRepository extends MongoRepository<Message, String> {
    List<Message> findByChatRoomIdOrderBySentAtAsc(String chatRoomId);
}
