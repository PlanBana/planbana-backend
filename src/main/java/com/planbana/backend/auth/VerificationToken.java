package com.planbana.backend.auth;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("verification_tokens")
public class VerificationToken {
  @Id
  private String id;

  @Indexed
  private String userId;

  @Indexed(unique = true)
  private String token;

  private Instant expiresAt;

  public VerificationToken() {}
  public VerificationToken(String userId, String token, Instant expiresAt) {
    this.userId = userId;
    this.token = token;
    this.expiresAt = expiresAt;
  }

  public String getId() { return id; }
  public String getUserId() { return userId; }
  public String getToken() { return token; }
  public Instant getExpiresAt() { return expiresAt; }
}
