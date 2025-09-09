package com.planbana.backend.chat;

import com.planbana.backend.common.BaseEntity;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("messages")
public class Message extends BaseEntity {
  private String conversationId;
  private String senderUserId;
  private String content;

  public String getConversationId() { return conversationId; }
  public void setConversationId(String conversationId) { this.conversationId = conversationId; }
  public String getSenderUserId() { return senderUserId; }
  public void setSenderUserId(String senderUserId) { this.senderUserId = senderUserId; }
  public String getContent() { return content; }
  public void setContent(String content) { this.content = content; }
}
