package com.planbana.backend.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends MongoRepository<Message, String> {
    List<Message> findByChatRoomIdOrderBySentAtAsc(String chatRoomId);

    List<Message> findByChatRoomIdOrderBySentAtDesc(String chatRoomId, Pageable pageable);

    Optional<Message> findFirstByChatRoomIdOrderBySentAtDesc(String chatRoomId);

    long countByChatRoomIdAndSentAtAfterAndSenderIdNot(String chatRoomId, Instant sentAt, String senderId);

    List<Message> findByChatRoomIdAndAttachmentSizeNotNull(String chatRoomId);

    List<Message> findBySenderIdAndAttachmentSizeNotNull(String senderId);
}
