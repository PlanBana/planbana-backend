package com.planbana.backend.common;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

public abstract class BaseEntity {
  @Id
  private String id;

  @CreatedDate
  private Instant createdAt;
  @LastModifiedDate
  private Instant updatedAt;

  @CreatedBy
  private String createdBy;
  @LastModifiedBy
  private String updatedBy;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public String getCreatedBy() { return createdBy; }
  public String getUpdatedBy() { return updatedBy; }
}
