package com.planbana.backend.user;

import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

  private final UserRepository repo;

  public UserController(UserRepository repo) {
    this.repo = repo;
  }

  @GetMapping("/me")
  public User me(Authentication auth) {
    return repo.findByEmail(auth.getName()).orElseThrow();
  }

  public static class UpdateMe {
    @Size(max=140) public String bio;
    public String displayName;
    public String avatarUrl;
    public String city;
    public Double latitude;
    public Double longitude;
  }

  @PatchMapping("/me")
  public Map<String, String> updateMe(@RequestBody UpdateMe req, Authentication auth) {
    User u = repo.findByEmail(auth.getName()).orElseThrow();
    if (req.bio != null) u.setBio(req.bio);
    if (req.displayName != null) u.setDisplayName(req.displayName);
    if (req.avatarUrl != null) u.setAvatarUrl(req.avatarUrl);
    if (req.city != null) u.setCity(req.city);
    if (req.latitude != null) u.setLatitude(req.latitude);
    if (req.longitude != null) u.setLongitude(req.longitude);
    repo.save(u);
    return Map.of("message", "updated");
  }
}
