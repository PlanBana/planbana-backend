package com.planbana.backend.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/users")
public class UserController {

  private final UserRepository repo;

  public UserController(UserRepository repo) {
    this.repo = repo;
  }

  @GetMapping("/me")
  public User me(Authentication auth) {
    return repo.findByPhone(auth.getName())
               .or(() -> repo.findByEmail(auth.getName()))
               .orElseThrow();
  }

  public static class UpdateMe {
    @Size(max=140) public String bio;
    public String displayName;
    public String avatarUrl;
    public String city;
    public Double latitude;
    public Double longitude;
    public String gender;
    public String occupation;
    public LocalDate birthDate;
    public List<String> languages;
    public Set<String> hobbies;

    // Intentionally NOT exposing govIdVerificationStatus here.
    // Verification flow is external; users cannot mark themselves VERIFIED.
  }

  @PatchMapping("/me")
  public Map<String, String> updateMe(@RequestBody UpdateMe req, Authentication auth) {
    User u = repo.findByPhone(auth.getName())
                 .or(() -> repo.findByEmail(auth.getName()))
                 .orElseThrow();

    if (req.bio != null) u.setBio(req.bio);
    if (req.displayName != null) u.setDisplayName(req.displayName);
    if (req.avatarUrl != null) u.setAvatarUrl(req.avatarUrl);
    if (req.city != null) u.setCity(req.city);
    if (req.latitude != null) u.setLatitude(req.latitude);
    if (req.longitude != null) u.setLongitude(req.longitude);
    if (req.gender != null) u.setGender(req.gender);
    if (req.occupation != null) u.setOccupation(req.occupation);
    if (req.birthDate != null) u.setBirthDate(req.birthDate);
    if (req.languages != null) u.setLanguages(req.languages);
    if (req.hobbies != null) u.setHobbies(req.hobbies);

    repo.save(u);
    return Map.of("message", "Profile updated");
  }

  // -------------------------
  // Verification status APIs
  // -------------------------

  /**
   * Get current government ID verification status.
   * Returns one of: UNVERIFIED, PENDING, VERIFIED, REJECTED
   */
  @GetMapping("/me/verification")
  public Map<String, String> myVerificationStatus(Authentication auth) {
    User u = repo.findByPhone(auth.getName())
                 .or(() -> repo.findByEmail(auth.getName()))
                 .orElseThrow();
    return Map.of("status", u.getGovIdVerificationStatus().name());
  }

  /**
   * Request (or re-request) verification.
   * Transitions:
   *   UNVERIFIED -> PENDING
   *   REJECTED   -> PENDING
   * If already PENDING/VERIFIED, it is a no-op and returns the current status.
   */
  @PostMapping("/me/verification")
  public Map<String, String> requestVerification(Authentication auth) {
    User u = repo.findByPhone(auth.getName())
                 .or(() -> repo.findByEmail(auth.getName()))
                 .orElseThrow();

    User.VerificationStatus current = u.getGovIdVerificationStatus();
    if (current == User.VerificationStatus.UNVERIFIED || current == User.VerificationStatus.REJECTED) {
      u.setGovIdVerificationStatus(User.VerificationStatus.PENDING);
      repo.save(u);
    }
    return Map.of("status", u.getGovIdVerificationStatus().name());
  }

  // -------------------------
  // Ratings APIs
  // -------------------------

  public static class RatingRequest {
    @Min(1) @Max(5)
    public Integer value;
  }

  /**
   * Read a user's rating summary.
   */
  @GetMapping("/{id}/rating")
  public Map<String, Object> getUserRating(@PathVariable String id) {
    User target = repo.findById(id).orElseThrow();
    return Map.of(
        "userId", target.getId(),
        "average", target.getRatingAverage(),
        "count", target.getRatingCount()
    );
  }

  /**
   * Upsert the caller's rating for a user (1..5).
   * - A user cannot rate themselves.
   * - Calling again overwrites the previous rating from the same rater.
   */
  @PostMapping("/{id}/rating")
  public Map<String, Object> rateUser(@PathVariable String id, @RequestBody RatingRequest body, Authentication auth) {
    if (body == null || body.value == null || body.value < 1 || body.value > 5) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating 'value' must be 1..5");
    }

    // rater
    User rater = repo.findByPhone(auth.getName())
                     .or(() -> repo.findByEmail(auth.getName()))
                     .orElseThrow();

    // target
    User target = repo.findById(id).orElseThrow();

    // prevent self-rating
    if (target.getId().equals(rater.getId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot rate yourself");
    }

    target.upsertRating(rater.getId(), body.value);
    repo.save(target);

    return Map.of(
        "message", "rating upserted",
        "userId", target.getId(),
        "average", target.getRatingAverage(),
        "count", target.getRatingCount()
    );
  }

  /**
   * Remove the caller's rating for a user.
   */
  @DeleteMapping("/{id}/rating")
  public Map<String, Object> removeMyRating(@PathVariable String id, Authentication auth) {
    User rater = repo.findByPhone(auth.getName())
                     .or(() -> repo.findByEmail(auth.getName()))
                     .orElseThrow();

    User target = repo.findById(id).orElseThrow();

    if (target.getId().equals(rater.getId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot remove self-rating");
    }

    target.removeRating(rater.getId());
    repo.save(target);

    return Map.of(
        "message", "rating removed",
        "userId", target.getId(),
        "average", target.getRatingAverage(),
        "count", target.getRatingCount()
    );
  }
}
