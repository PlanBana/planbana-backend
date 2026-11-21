package com.planbana.backend.chat;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface ChatUserStateRepository extends MongoRepository<ChatUserState, String> {

    Optional<ChatUserState> findByChatRoomIdAndUserId(String chatRoomId, String userId);

    List<ChatUserState> findByUserId(String userId);

    void deleteByChatRoomIdAndUserId(String chatRoomId, String userId);
}