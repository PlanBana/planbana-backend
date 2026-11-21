package com.planbana.backend.events.share;

import com.planbana.backend.common.BaseEntity;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("share_links")
public class ShareLink extends BaseEntity {

  public enum Type { PUBLIC, INVITE }
  public enum Scope { VIEW, JOIN, VIEW_JOIN }

  @Indexed
  private String eventId;

  private String createdByUserId;

  private Type type = Type.INVITE;
  private Scope scope = Scope.VIEW;

  /** sha256(code) in hex. Null for PUBLIC links (no code). */
  @Indexed(unique = true, sparse = true)
  private String codeHash;

  /** Limits / analytics */
  private Instant expiresAt;      // nullable
  private Integer maxUses;        // nullable => unlimited
  private Integer uses = 0;       // increments on resolve/redirect
  private boolean disabled = false;

  /** Optional tracking fields */
  private String channel;
  private String campaign;

  // ---- helpers ----
  public boolean isValidAt(Instant now) {
    if (disabled) return false;
    if (expiresAt != null && now.isAfter(expiresAt)) return false;
    if (maxUses != null && uses != null && uses >= maxUses) return false;
    return true;
  }

  // ---- getters/setters ----
  public String getEventId() { return eventId; }
  public void setEventId(String eventId) { this.eventId = eventId; }

  public String getCreatedByUserId() { return createdByUserId; }
  public void setCreatedByUserId(String createdByUserId) { this.createdByUserId = createdByUserId; }

  public Type getType() { return type; }
  public void setType(Type type) { this.type = type; }

  public Scope getScope() { return scope; }
  public void setScope(Scope scope) { this.scope = scope; }

  public String getCodeHash() { return codeHash; }
  public void setCodeHash(String codeHash) { this.codeHash = codeHash; }

  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

  public Integer getMaxUses() { return maxUses; }
  public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }

  public Integer getUses() { return uses; }
  public void setUses(Integer uses) { this.uses = uses; }

  public boolean isDisabled() { return disabled; }
  public void setDisabled(boolean disabled) { this.disabled = disabled; }

  public String getChannel() { return channel; }
  public void setChannel(String channel) { this.channel = channel; }

  public String getCampaign() { return campaign; }
  public void setCampaign(String campaign) { this.campaign = campaign; }
}
