package com.planbana.backend.user;

import com.planbana.backend.common.BaseEntity;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.*;

@Document("users")
public class User extends BaseEntity {

  @Indexed(unique = true, sparse = true)
  private String email;

  @Indexed(unique = true)
  private String phone;

  private String passwordHash;
  private String displayName;
  private String bio;
  private String avatarUrl;

  private boolean emailVerified = false;
  private boolean phoneVerified = false;

  private Set<String> roles = new HashSet<>();
  private List<String> languages = new ArrayList<>();
  private Set<String> hobbies = new HashSet<>();

  private String gender; // "male", "female", "trans", etc.
  private LocalDate birthDate;
  private String occupation;

  private String city;
  private Double latitude;
  private Double longitude;

  // === Government ID verification status (workflow handled elsewhere) ===
  public enum VerificationStatus {
    UNVERIFIED,   // default
    PENDING,      // submitted, awaiting review
    VERIFIED,     // approved
    REJECTED      // rejected (e.g., mismatch/invalid)
  }

  private VerificationStatus govIdVerificationStatus = VerificationStatus.UNVERIFIED;

  // === Ratings ===
  // Stores per-rater values; key = rater userId, value = 1..5
  // NOTE: This map is not exposed via controller responses.
  private Map<String, Integer> ratingsByUserId = new HashMap<>();

  // Derived aggregates for fast reads
  private long ratingCount = 0L;
  private double ratingAverage = 0.0;

  // ---------- derived fields helpers ----------
  public void upsertRating(String raterUserId, int value) {
    if (raterUserId == null || raterUserId.isBlank()) return;
    if (value < 1 || value > 5) return;

    Integer previous = ratingsByUserId.put(raterUserId, value);
    recomputeRatings(previous, value);
  }

  public void removeRating(String raterUserId) {
    if (raterUserId == null || raterUserId.isBlank()) return;
    Integer previous = ratingsByUserId.remove(raterUserId);
    if (previous != null) {
      // full recompute to keep it simple & correct
      fullRecompute();
    }
  }

  private void recomputeRatings(Integer previous, int value) {
    if (previous == null) {
      // new rating
      ratingCount = ratingCount + 1;
    }
    // recompute average from map to avoid numeric drift
    fullRecompute();
  }

  private void fullRecompute() {
    long cnt = ratingsByUserId.size();
    long sum = 0;
    for (Integer v : ratingsByUserId.values()) {
      if (v != null) sum += v;
    }
    this.ratingCount = cnt;
    this.ratingAverage = cnt == 0 ? 0.0 : (double) sum / (double) cnt;
  }

  // ---------- getters/setters ----------

  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }

  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }

  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }

  public String getBio() { return bio; }
  public void setBio(String bio) { this.bio = bio; }

  public String getAvatarUrl() { return avatarUrl; }
  public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

  public boolean isEmailVerified() { return emailVerified; }
  public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

  public boolean isPhoneVerified() { return phoneVerified; }
  public void setPhoneVerified(boolean phoneVerified) { this.phoneVerified = phoneVerified; }

  public Set<String> getRoles() { return roles; }
  public void setRoles(Set<String> roles) { this.roles = roles; }

  public List<String> getLanguages() { return languages; }
  public void setLanguages(List<String> languages) { this.languages = languages; }

  public Set<String> getHobbies() { return hobbies; }
  public void setHobbies(Set<String> hobbies) { this.hobbies = hobbies; }

  public String getGender() { return gender; }
  public void setGender(String gender) { this.gender = gender; }

  public LocalDate getBirthDate() { return birthDate; }
  public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

  public String getOccupation() { return occupation; }
  public void setOccupation(String occupation) { this.occupation = occupation; }

  public String getCity() { return city; }
  public void setCity(String city) { this.city = city; }

  public Double getLatitude() { return latitude; }
  public void setLatitude(Double latitude) { this.latitude = latitude; }

  public Double getLongitude() { return longitude; }
  public void setLongitude(Double longitude) { this.longitude = longitude; }

  public VerificationStatus getGovIdVerificationStatus() { return govIdVerificationStatus; }
  public void setGovIdVerificationStatus(VerificationStatus govIdVerificationStatus) {
    this.govIdVerificationStatus = govIdVerificationStatus;
  }

  // Ratings (aggregates)
  public long getRatingCount() { return ratingCount; }
  public void setRatingCount(long ratingCount) { this.ratingCount = ratingCount; }

  public double getRatingAverage() { return ratingAverage; }
  public void setRatingAverage(double ratingAverage) { this.ratingAverage = ratingAverage; }

  // For persistence; do not expose in API responses
  public Map<String, Integer> getRatingsByUserId() { return ratingsByUserId; }
  public void setRatingsByUserId(Map<String, Integer> ratingsByUserId) {
    this.ratingsByUserId = ratingsByUserId == null ? new HashMap<>() : ratingsByUserId;
    // ensure aggregates consistent
    fullRecompute();
  }
}
