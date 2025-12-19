package com.planbana.backend.user;

import com.planbana.backend.common.BaseEntity;
import com.planbana.backend.user.User.AdminNote;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Document("users")
public class User extends BaseEntity {

  @Indexed(unique = true, sparse = true)
  private String email;

  @Indexed(unique = true)
  private String phone;

  private String passwordHash;

  // ✅ Store Firebase UID
  @Indexed(unique = true)
  private String firebaseUid;

  // NEW: canonical full name
  private String name;

  // Optional display/handle shown in UI
  private String displayName;
  private String avatarUrl;

  private Boolean disabled = false;

  private String bio;

  private boolean emailVerified = false;
  private boolean phoneVerified = false;

  @Field("roles")
  private Set<String> roles = new HashSet<>();
  private List<String> languages = new ArrayList<>();
  private Set<String> hobbies = new HashSet<>();

  private String gender;
  private LocalDate birthDate;

  private String occupation;
  private String company;

  private String state;
  private String country;
  private String city;
  private Double latitude;
  private Double longitude;

  private Set<String> bookmarkedEventIds = new HashSet<>();

  private String profilePhotoUrl;

  // === Government ID verification status ===
  public enum VerificationStatus {
    UNVERIFIED,
    PENDING,
    VERIFIED,
    REJECTED
  }

  private VerificationStatus govIdVerificationStatus = VerificationStatus.UNVERIFIED;

  // === Ratings ===
  private Map<String, Integer> ratingsByUserId = new HashMap<>();
  private long ratingCount = 0L;
  private double ratingAverage = 0.0;

  private Integer tokenVersion = 0;

  public Integer getTokenVersion() {
    return tokenVersion == null ? 0 : tokenVersion;
  }

  public void incrementTokenVersion() {
    this.tokenVersion = getTokenVersion() + 1;
  }

  // ---------- rating helpers ----------
  public void upsertRating(String raterUserId, int value) {
    if (raterUserId == null || raterUserId.isBlank())
      return;
    if (value < 1 || value > 5)
      return;

    Integer previous = ratingsByUserId.put(raterUserId, value);
    recomputeRatings(previous, value);
  }

  public void removeRating(String raterUserId) {
    if (raterUserId == null || raterUserId.isBlank())
      return;
    Integer previous = ratingsByUserId.remove(raterUserId);
    if (previous != null) {
      fullRecompute();
    }
  }

  private void recomputeRatings(Integer previous, int value) {
    if (previous == null) {
      ratingCount = ratingCount + 1;
    }
    fullRecompute();
  }

  private void fullRecompute() {
    long cnt = ratingsByUserId.size();
    long sum = 0;
    for (Integer v : ratingsByUserId.values()) {
      if (v != null)
        sum += v;
    }
    this.ratingCount = cnt;
    this.ratingAverage = cnt == 0 ? 0.0 : (double) sum / (double) cnt;
  }

  // ---------- getters/setters ----------

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public Boolean getDisabled() {
    return disabled;
  }

  public void setDisabled(Boolean disabled) {
    this.disabled = disabled;
  }

  public String getBio() {
    return bio;
  }

  public void setBio(String bio) {
    this.bio = bio;
  }

  public String getFirebaseUid() {
    return firebaseUid;
  }

  public void setFirebaseUid(String firebaseUid) {
    this.firebaseUid = firebaseUid;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public void setAvatarUrl(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  public boolean isEmailVerified() {
    return emailVerified;
  }

  public void setEmailVerified(boolean emailVerified) {
    this.emailVerified = emailVerified;
  }

  public boolean isPhoneVerified() {
    return phoneVerified;
  }

  public void setPhoneVerified(boolean phoneVerified) {
    this.phoneVerified = phoneVerified;
  }

  public Set<String> getRoles() {
    return roles;
  }

  public void setRoles(Set<String> roles) {
    this.roles = roles;
  }

  public List<String> getLanguages() {
    return languages;
  }

  public void setLanguages(List<String> languages) {
    this.languages = languages;
  }

  public Set<String> getHobbies() {
    return hobbies;
  }

  public void setHobbies(Set<String> hobbies) {
    this.hobbies = hobbies;
  }

  public String getGender() {
    return gender;
  }

  public void setGender(String gender) {
    this.gender = gender;
  }

  public LocalDate getBirthDate() {
    return birthDate;
  }

  public void setBirthDate(LocalDate birthDate) {
    this.birthDate = birthDate;
  }

  public String getOccupation() {
    return occupation;
  }

  public void setOccupation(String occupation) {
    this.occupation = occupation;
  }

  public String getCompany() {
    return company;
  }

  public void setCompany(String company) {
    this.company = company;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public Double getLatitude() {
    return latitude;
  }

  public void setLatitude(Double latitude) {
    this.latitude = latitude;
  }

  public Double getLongitude() {
    return longitude;
  }

  public void setLongitude(Double longitude) {
    this.longitude = longitude;
  }

  public VerificationStatus getGovIdVerificationStatus() {
    return govIdVerificationStatus;
  }

  public void setGovIdVerificationStatus(VerificationStatus govIdVerificationStatus) {
    this.govIdVerificationStatus = govIdVerificationStatus;
  }

  public long getRatingCount() {
    return ratingCount;
  }

  public void setRatingCount(long ratingCount) {
    this.ratingCount = ratingCount;
  }

  public double getRatingAverage() {
    return ratingAverage;
  }

  public void setRatingAverage(double ratingAverage) {
    this.ratingAverage = ratingAverage;
  }

  public Map<String, Integer> getRatingsByUserId() {
    return ratingsByUserId;
  }

  public void setRatingsByUserId(Map<String, Integer> ratingsByUserId) {
    this.ratingsByUserId = ratingsByUserId == null ? new HashMap<>() : ratingsByUserId;
    fullRecompute();
  }

  public Set<String> getBookmarkedEventIds() {
    return bookmarkedEventIds;
  }

  public void setBookmarkedEventIds(Set<String> bookmarkedEventIds) {
    this.bookmarkedEventIds = bookmarkedEventIds == null ? new HashSet<>() : bookmarkedEventIds;
  }

  // ============================
  // Moderation Fields
  // ============================
  private String disabledReason;
  private Instant disabledAt;

  // Admin Notes (multiple)
  private List<AdminNote> adminNotes = new ArrayList<>();

  public String getDisabledReason() {
    return disabledReason;
  }

  public void setDisabledReason(String disabledReason) {
    this.disabledReason = disabledReason;
  }

  public Instant getDisabledAt() {
    return disabledAt;
  }

  public void setDisabledAt(Instant disabledAt) {
    this.disabledAt = disabledAt;
  }

  public List<AdminNote> getAdminNotes() {
    return adminNotes;
  }

  public void setAdminNotes(List<AdminNote> adminNotes) {
    this.adminNotes = adminNotes;
  }

  public static class AdminNote {
    public String id;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String adminId;

    public String getAdminId() {
      return adminId;
    }

    public void setAdminId(String adminId) {
      this.adminId = adminId;
    }

    public String note;

    public String getNote() {
      return note;
    }

    public void setNote(String note) {
      this.note = note;
    }

    public Instant createdAt = Instant.now();

    public Instant getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
      this.createdAt = createdAt;
    }

    public AdminNote() {
    }

    public AdminNote(String adminId, String note) {
      this.id = UUID.randomUUID().toString();
      this.adminId = adminId;
      this.note = note;
      this.createdAt = Instant.now();
    }
  }

  // This is for banning User from the entire platform
  private Boolean banned = false;

  public void setBanned(Boolean banned) {
    this.banned = banned;
  }

  public Boolean getBanned() {
    return banned;
  }

  private String bannedReason;

  public String getBannedReason() {
    return bannedReason;
  }

  public void setBannedReason(String bannedReason) {
    this.bannedReason = bannedReason;
  }

  private Instant bannedAt;

  public Instant getBannedAt() {
    return bannedAt;
  }

  public void setBannedAt(Instant bannedAt) {
    this.bannedAt = bannedAt;
  }

  private List<String> moderatorRegions = new ArrayList<>();

  public List<String> getModeratorRegions() {
    return moderatorRegions;
  }

  public void setModeratorRegions(List<String> moderatorRegions) {
    this.moderatorRegions = moderatorRegions;
  }

  public boolean hasRole(String string) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'hasRole'");
  }

}
