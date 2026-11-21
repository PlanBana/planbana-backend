package com.planbana.backend.notification;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.planbana.backend.common.BaseEntity;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * User-facing notification stored in {@code notifications}.
 * Emitted at relevant action points (e.g. join-request events) so the
 * recipient can be alerted via the bell UI and/or a live WebSocket topic.
 */
@Document("notifications")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Notification extends BaseEntity {

  /** Who this notification belongs to. */
  @Indexed
  private String recipientUserId;

  /** Business type, e.g. JOIN_REQUEST, JOIN_APPROVED, JOIN_REJECTED. */
  private String type;

  /** Related event id (if applicable). */
  private String eventId;

  /** User who triggered the notification (if applicable). */
  private String actorUserId;

  /** Short human-readable title shown in the bell. */
  private String title;

  /** Longer human-readable body shown in the bell. */
  private String body;

  private boolean read = false;

  public String getRecipientUserId() { return recipientUserId; }
  public void setRecipientUserId(String recipientUserId) { this.recipientUserId = recipientUserId; }

  public String getType() { return type; }
  public void setType(String type) { this.type = type; }

  public String getEventId() { return eventId; }
  public void setEventId(String eventId) { this.eventId = eventId; }

  public String getActorUserId() { return actorUserId; }
  public void setActorUserId(String actorUserId) { this.actorUserId = actorUserId; }

  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }

  public String getBody() { return body; }
  public void setBody(String body) { this.body = body; }

  public boolean isRead() { return read; }
  public void setRead(boolean read) { this.read = read; }
}