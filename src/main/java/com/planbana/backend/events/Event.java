package com.planbana.backend.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
  private String imageUrl;

  @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
  private GeoJsonPoint location;

  @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
  private GeoJsonPoint startLocation;

  @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
  private GeoJsonPoint destinationLocation;

  private String category;
  private String price;
  private String pricingType;
  private Integer maxParticipants;

  private String requirements;
  private String additionalGuidelines;

  private Set<String> tags = new HashSet<>();
  private String createdByUserId;

  private Set<String> participants = new HashSet<>();
  private List<JoinRequest> joinRequests = new ArrayList<>();

  private Set<String> coHostUserIds = new HashSet<>(); // ✅ Single source for co-hosts

  private boolean isCanceled = false;

  @JsonProperty("participantsWaitingList")
  private Set<String> participantsWaitingList = new HashSet<>();

  @JsonIgnore
  private Set<String> likedByUserIds = new HashSet<>();
  private long likeCount = 0L;

  // ===== Join Request & Status Enum =====

  public enum JoinStatus {
    NONE, PENDING, APPROVED, REJECTED, WAITLISTED
  }

  public static class JoinRequest {
    private String userId;
    private JoinStatus status;
    private Instant requestedAt;

    public JoinRequest() {
    }

    public JoinRequest(String userId, JoinStatus status, Instant requestedAt) {
      this.userId = userId;
      this.status = status;
      this.requestedAt = requestedAt;
    }

    public String getUserId() {
      return userId;
    }

    public void setUserId(String userId) {
      this.userId = userId;
    }

    public JoinStatus getStatus() {
      return status;
    }

    public void setStatus(JoinStatus status) {
      this.status = status;
    }

    public Instant getRequestedAt() {
      return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
      this.requestedAt = requestedAt;
    }
  }

  // ====== Likes ======
  public boolean like(String userId) {
    if (userId == null || userId.isBlank())
      return false;
    boolean added = likedByUserIds.add(userId);
    if (added)
      recomputeLikes();
    return added;
  }

  public boolean unlike(String userId) {
    if (userId == null || userId.isBlank())
      return false;
    boolean removed = likedByUserIds.remove(userId);
    if (removed)
      recomputeLikes();
    return removed;
  }

  public void recomputeLikes() {
    this.likeCount = likedByUserIds.size();
  }

  @JsonProperty("spotsLeft")
  public Integer getSpotsLeft() {
    if (maxParticipants == null)
      return null;
    int left = maxParticipants - (participants == null ? 0 : participants.size());
    return Math.max(left, 0);
  }

  // ===== Getters / Setters =====

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Instant getStartAt() {
    return startAt;
  }

  public void setStartAt(Instant startAt) {
    this.startAt = startAt;
  }

  public Instant getEndAt() {
    return endAt;
  }

  public void setEndAt(Instant endAt) {
    this.endAt = endAt;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public GeoJsonPoint getLocation() {
    return location;
  }

  public void setLocation(GeoJsonPoint location) {
    this.location = location;
  }

  public GeoJsonPoint getStartLocation() {
    return startLocation;
  }

  public void setStartLocation(GeoJsonPoint startLocation) {
    this.startLocation = startLocation;
  }

  public GeoJsonPoint getDestinationLocation() {
    return destinationLocation;
  }

  public void setDestinationLocation(GeoJsonPoint destinationLocation) {
    this.destinationLocation = destinationLocation;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getPrice() {
    return price;
  }

  public void setPrice(String price) {
    this.price = price;
  }

  public String getPricingType() {
    return pricingType;
  }

  public void setPricingType(String pricingType) {
    this.pricingType = pricingType;
  }

  public Integer getMaxParticipants() {
    return maxParticipants;
  }

  public void setMaxParticipants(Integer maxParticipants) {
    this.maxParticipants = maxParticipants;
  }

  public String getRequirements() {
    return requirements;
  }

  public void setRequirements(String requirements) {
    this.requirements = requirements;
  }

  public String getAdditionalGuidelines() {
    return additionalGuidelines;
  }

  public void setAdditionalGuidelines(String additionalGuidelines) {
    this.additionalGuidelines = additionalGuidelines;
  }

  public Set<String> getTags() {
    return tags;
  }

  public void setTags(Set<String> tags) {
    this.tags = tags;
  }

  public String getCreatedByUserId() {
    return createdByUserId;
  }

  public void setCreatedByUserId(String createdByUserId) {
    this.createdByUserId = createdByUserId;
  }

  public Set<String> getParticipants() {
    return participants;
  }

  public void setParticipants(Set<String> participants) {
    this.participants = participants;
  }

  public List<JoinRequest> getJoinRequests() {
    return joinRequests;
  }

  public void setJoinRequests(List<JoinRequest> joinRequests) {
    this.joinRequests = joinRequests;
  }

  public long getLikeCount() {
    return likeCount;
  }

  public void setLikeCount(long likeCount) {
    this.likeCount = likeCount;
  }

  public Set<String> getLikedByUserIds() {
    return likedByUserIds;
  }

  public void setLikedByUserIds(Set<String> likedByUserIds) {
    this.likedByUserIds = likedByUserIds == null ? new HashSet<>() : likedByUserIds;
    recomputeLikes();
  }

  public Set<String> getCoHostUserIds() {
    return coHostUserIds;
  }

  public void setCoHostUserIds(Set<String> coHostUserIds) {
    this.coHostUserIds = coHostUserIds == null ? new HashSet<>() : coHostUserIds;
  }

  // ===== Utility Methods (Optional) =====

  public boolean isApprovedParticipant(String userId) {
    return participants != null && participants.contains(userId);
  }

  public boolean isCoHost(String userId) {
    return coHostUserIds != null && coHostUserIds.contains(userId);
  }

  public boolean isHost(String userId) {
    return userId != null && userId.equals(createdByUserId);
  }

  public boolean canManageEvent(String userId) {
    return isHost(userId) || isCoHost(userId);
  }

  public Set<String> getParticipantsWaitingList() {
    return participantsWaitingList;
  }

  public void setParticipantsWaitingList(Set<String> participantsWaitingList) {
    this.participantsWaitingList = participantsWaitingList == null ? new HashSet<>() : participantsWaitingList;
  }

  public boolean isCanceled() {
    return isCanceled;
  }

  public void setCanceled(boolean canceled) {
    isCanceled = canceled;
  }
}
