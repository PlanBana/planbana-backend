package com.planbana.backend.user;

import com.planbana.backend.common.BaseEntity;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashSet;
import java.util.Set;

@Document("users")
public class User extends BaseEntity {

  @Indexed(unique = true)
  private String email;
  private String passwordHash;

  private String displayName;
  private String bio;
  private String avatarUrl;

  private boolean emailVerified = false;
  private Set<String> roles = new HashSet<>(); // USER, ADMIN

  private Set<String> interests = new HashSet<>();
  private String city;
  private Double latitude;
  private Double longitude;

  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
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
  public Set<String> getRoles() { return roles; }
  public void setRoles(Set<String> roles) { this.roles = roles; }
  public Set<String> getInterests() { return interests; }
  public void setInterests(Set<String> interests) { this.interests = interests; }
  public String getCity() { return city; }
  public void setCity(String city) { this.city = city; }
  public Double getLatitude() { return latitude; }
  public void setLatitude(Double latitude) { this.latitude = latitude; }
  public Double getLongitude() { return longitude; }
  public void setLongitude(Double longitude) { this.longitude = longitude; }
}
