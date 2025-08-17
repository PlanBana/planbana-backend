package com.planbana.backend.events;

import com.planbana.backend.common.BaseEntity;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Document("events")
public class Event extends BaseEntity {

  private String title;
  private String description;
  private Instant startAt;
  private Instant endAt;

  @GeoSpatialIndexed(type = org.springframework.data.mongodb.core.index.GeoSpatialIndexType.GEO_2DSPHERE)
  private GeoJsonPoint location; // lon,lat

  private Set<String> tags = new HashSet<>();
  private String createdByUserId;

  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public Instant getStartAt() { return startAt; }
  public void setStartAt(Instant startAt) { this.startAt = startAt; }
  public Instant getEndAt() { return endAt; }
  public void setEndAt(Instant endAt) { this.endAt = endAt; }
  public GeoJsonPoint getLocation() { return location; }
  public void setLocation(GeoJsonPoint location) { this.location = location; }
  public Set<String> getTags() { return tags; }
  public void setTags(Set<String> tags) { this.tags = tags; }
  public String getCreatedByUserId() { return createdByUserId; }
  public void setCreatedByUserId(String createdByUserId) { this.createdByUserId = createdByUserId; }
}
