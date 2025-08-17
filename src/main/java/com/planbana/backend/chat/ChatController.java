package com.planbana.backend.chat;

import com.planbana.backend.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

  private final ConversationRepository conversations;
  private final MessageRepository messages;
  private final UserRepository users;
  private final SimpMessagingTemplate broker;

  public ChatController(ConversationRepository conversations, MessageRepository messages, UserRepository users, SimpMessagingTemplate broker) {
    this.conversations = conversations;
    this.messages = messages;
    this.users = users;
    this.broker = broker;
  }

  @PostMapping("/conversations")
  public Conversation createConversation(@RequestBody Map<String, String> body, Authentication auth) {
    var otherUserId = body.get("otherUserId");
    var c = new Conversation();
    var me = users.findByEmail(auth.getName()).orElseThrow();
    c.getParticipantUserIds().add(me.getId());
    c.getParticipantUserIds().add(otherUserId);
    return conversations.save(c);
  }

  @GetMapping("/conversations/{id}/messages")
  public Object listMessages(@PathVariable String id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    return messages.findByConversationIdOrderByCreatedAtAsc(id, PageRequest.of(page, size));
  }

  // STOMP endpoint: client sends to /app/conversations/{id}/send
  @MessageMapping("/conversations/{id}/send")
  public void send(@DestinationVariable String id, String content, Authentication auth) {
    var me = users.findByEmail(auth.getName()).orElseThrow();
    var msg = new Message();
    msg.setConversationId(id);
    msg.setSenderUserId(me.getId());
    msg.setContent(content);
    messages.save(msg);
    broker.convertAndSend("/topic/conversations." + id, msg);
  }
}
