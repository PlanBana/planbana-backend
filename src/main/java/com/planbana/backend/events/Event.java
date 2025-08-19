package com.planbana.backend.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.planbana.backend.common.BaseEntity;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.*;

@Document("events")
public class Event extends BaseEntity {

  private String title;
  private String description;
  private Instant startAt;
  private Instant endAt;

  /** Cover image URL for the event (optional) */
  private String imageUrl;

  /** Legacy single-point; kept for backward-compat if you were already using it. */
  @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
  private GeoJsonPoint location; // lon,lat

  /** Start and destination points */
  @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
  private GeoJsonPoint startLocation; // lon,lat

  @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
  private GeoJsonPoint destinationLocation; // lon,lat

  /** High-level classification for the event */
  private String category;

  /** Pricing fields */
  private String pricingType; // e.g., "FREE", "PAID", "SPLIT"
  private Double price;       // nullable; depends on pricingType
  private Integer maxParticipants; // nullable => unlimited

  private Set<String> tags = new HashSet<>();
  private String createdByUserId;

  /** People who are confirmed to participate */
  private Set<String> participants = new HashSet<>();

  /** Pending/waitlisted/approved/rejected requests (audit-friendly) */
  private List<JoinRequest> joinRequests = new ArrayList<>();

  /** Likes (stored), and public aggregate */
  @JsonIgnore
  private Set<String> likedByUserIds = new HashSet<>();
  private long likeCount = 0L;

  public enum JoinStatus {
    NONE, PENDING, APPROVED, REJECTED, WAITLISTED
  }

  public static class JoinRequest {
    private String userId;
    private JoinStatus status;
    private Instant requestedAt;

    public JoinRequest() {}

    public JoinRequest(String userId, JoinStatus status, Instant requestedAt) {
      this.userId = userId;
      this.status = status;
      this.requestedAt = requestedAt;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public JoinStatus getStatus() { return status; }
    public void setStatus(JoinStatus status) { this.status = status; }
    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
  }

  // ---- Like helpers ----
  public boolean like(String userId) {
    if (userId == null || userId.isBlank()) return false;
    boolean added = likedByUserIds.add(userId);
    if (added) recomputeLikes();
    return added;
  }

  public boolean unlike(String userId) {
    if (userId == null || userId.isBlank()) return false;
    boolean removed = likedByUserIds.remove(userId);
    if (removed) recomputeLikes();
    return removed;
  }

  public void recomputeLikes() {
    this.likeCount = likedByUserIds.size();
  }

  // --- getters/setters ---

  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }

  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }

  public Instant getStartAt() { return startAt; }
  public void setStartAt(Instant startAt) { this.startAt = startAt; }

  public Instant getEndAt() { return endAt; }
  public void setEndAt(Instant endAt) { this.endAt = endAt; }

  public String getImageUrl() { return imageUrl; }
  public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

  public GeoJsonPoint getLocation() { return location; }
  public void setLocation(GeoJsonPoint location) { this.location = location; }

  public GeoJsonPoint getStartLocation() { return startLocation; }
  public void setStartLocation(GeoJsonPoint startLocation) { this.startLocation = startLocation; }

  public GeoJsonPoint getDestinationLocation() { return destinationLocation; }
  public void setDestinationLocation(GeoJsonPoint destinationLocation) { this.destinationLocation = destinationLocation; }

  public String getCategory() { return category; }
  public void setCategory(String category) { this.category = category; }

  public String getPricingType() { return pricingType; }
  public void setPricingType(String pricingType) { this.pricingType = pricingType; }

  public Double getPrice() { return price; }
  public void setPrice(Double price) { this.price = price; }

  public Integer getMaxParticipants() { return maxParticipants; }
  public void setMaxParticipants(Integer maxParticipants) { this.maxParticipants = maxParticipants; }

  public Set<String> getTags() { return tags; }
  public void setTags(Set<String> tags) { this.tags = tags; }

  public String getCreatedByUserId() { return createdByUserId; }
  public void setCreatedByUserId(String createdByUserId) { this.createdByUserId = createdByUserId; }

  public Set<String> getParticipants() { return participants; }
  public void setParticipants(Set<String> participants) { this.participants = participants; }

  public List<JoinRequest> getJoinRequests() { return joinRequests; }
  public void setJoinRequests(List<JoinRequest> joinRequests) { this.joinRequests = joinRequests; }

  public long getLikeCount() { return likeCount; }
  public void setLikeCount(long likeCount) { this.likeCount = likeCount; }

  @JsonIgnore
  public Set<String> getLikedByUserIds() { return likedByUserIds; }
  public void setLikedByUserIds(Set<String> likedByUserIds) {
    this.likedByUserIds = likedByUserIds == null ? new HashSet<>() : likedByUserIds;
    recomputeLikes();
  }
}
