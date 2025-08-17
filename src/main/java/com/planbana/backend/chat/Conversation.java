package com.planbana.backend.chat;

import com.planbana.backend.common.BaseEntity;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashSet;
import java.util.Set;

@Document("conversations")
public class Conversation extends BaseEntity {
  private Set<String> participantUserIds = new HashSet<>();
  public Set<String> getParticipantUserIds() { return participantUserIds; }
  public void setParticipantUserIds(Set<String> participantUserIds) { this.participantUserIds = participantUserIds; }
}
