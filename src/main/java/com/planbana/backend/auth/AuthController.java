package com.planbana.backend.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.planbana.backend.auth.dto.AuthDtos;
import com.planbana.backend.security.JwtService;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final JwtService jwt;

  public AuthController(UserRepository users,
      PasswordEncoder encoder,
      JwtService jwt) {
    this.users = users;
    this.encoder = encoder;
    this.jwt = jwt;
  }

  private static String normalizePhone(String phone) {
    if (phone == null)
      return null;
    return phone.trim().replaceAll("\\s+", "").replaceAll("[^0-9]", "");
  }

  @PostMapping("/check-phone")
  public ResponseEntity<?> checkPhone(@Valid @RequestBody AuthDtos.CheckPhoneRequest req) {
    try {
      FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(req.firebaseIdToken);
      String uid = decoded.getUid();

      String phone = normalizePhone(FirebaseAuth.getInstance().getUser(uid).getPhoneNumber());
      if (phone == null || phone.isEmpty()) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid Firebase token: phone missing"));
      }

      Optional<User> existing = users.findByPhone(phone);
      if (existing.isPresent()) {
        return ResponseEntity.ok(Map.of("status", "LOGIN_REQUIRED", "phone", phone));
      } else {
        return ResponseEntity.ok(Map.of("status", "REGISTER_REQUIRED", "phone", phone, "firebaseUid", uid));
      }
    } catch (Exception e) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "Firebase token invalid/expired", "details", e.getMessage()));
    }
  }

  @PostMapping("/register-minimal")
  public ResponseEntity<?> registerMinimal(@Valid @RequestBody AuthDtos.RegisterMinimalRequest req,
      HttpServletResponse res) {
    try {
      FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(req.firebaseIdToken);
      String uid = decoded.getUid();
      String phone = normalizePhone(FirebaseAuth.getInstance().getUser(uid).getPhoneNumber());

      if (phone == null || phone.isEmpty()) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid Firebase token: phone missing"));
      }

      if (users.findByPhone(phone).isPresent()) {
        return ResponseEntity.badRequest().body(Map.of("error", "Phone already registered"));
      }

      User u = new User();
      u.setFirebaseUid(uid);
      u.setPhone(phone);
      u.setPasswordHash(encoder.encode(req.password));
      u.setPhoneVerified(true);
      u.setRoles(Set.of("USER"));

      if (u.getLanguages() == null || u.getLanguages().isEmpty()) {
        u.setLanguages(List.of("English"));
      }
      users.save(u);

      // ✅ Prefix roles with ROLE_ in JWT
      Set<String> jwtRoles = u.getRoles().stream()
          .map(r -> "ROLE_" + r)
          .collect(Collectors.toSet());

      String access = jwt.generateAccess(u.getPhone(), jwtRoles);
      String refresh = jwt.generateRefresh(u.getPhone());

      Cookie cookie = new Cookie("access_token", access);
      cookie.setPath("/");
      cookie.setHttpOnly(true);
      cookie.setSecure(false);
      cookie.setAttribute("SameSite", "None");
      res.addCookie(cookie);

      return ResponseEntity
          .ok(Map.of("message", "Registered successfully", "accessToken", access, "refreshToken", refresh));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("error", "Registration failed", "details", e.getMessage()));
    }
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
    String refresh = body.get("refreshToken");
    if (refresh == null || !jwt.validateToken(refresh)) {
      return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired refresh token"));
    }

    String username = jwt.getUsername(refresh);
    List<String> roles = jwt.getRoles(refresh);

    String newAccess = jwt.generateAccess(username, Set.copyOf(roles));
    String newRefresh = jwt.generateRefresh(username); // optional rotation

    return ResponseEntity.ok(Map.of(
        "accessToken", newAccess,
        "refreshToken", newRefresh));
  }

  @PostMapping("/login-firebase")
  public ResponseEntity<?> loginWithFirebase(@Valid @RequestBody AuthDtos.FirebaseLoginRequest req,
      HttpServletResponse res) {
    try {
      FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(req.firebaseIdToken);
      String uid = decoded.getUid();
      String phone = normalizePhone(FirebaseAuth.getInstance().getUser(uid).getPhoneNumber());

      if (phone == null || phone.isEmpty()) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid Firebase token: phone missing"));
      }

      Optional<User> existing = users.findByPhone(phone);
      if (existing.isEmpty()) {
        return ResponseEntity.status(404).body(Map.of("error", "User not found"));
      }

      User u = existing.get();

      // ❗ ADD THIS CHECK HERE
      if (u.getDisabled() != null && u.getDisabled()) {
        return ResponseEntity.status(403)
            .body(Map.of("error", "Your account has been disabled by the admin"));
      }

      if (!encoder.matches(req.password, u.getPasswordHash())) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid password"));
      }

      // ✅ Prefix roles with ROLE_ in JWT
      Set<String> jwtRoles = u.getRoles().stream()
          .map(r -> "ROLE_" + r)
          .collect(Collectors.toSet());

      String access = jwt.generateAccess(u.getPhone(), jwtRoles);
      String refresh = jwt.generateRefresh(u.getPhone());

      Cookie cookie = new Cookie("access_token", access);
      cookie.setPath("/");
      cookie.setHttpOnly(true);
      cookie.setSecure(true);
      cookie.setAttribute("SameSite", "Lax");
      res.addCookie(cookie);

      return ResponseEntity.ok(Map.of("message", "Login successful", "accessToken", access, "refreshToken", refresh));
    } catch (Exception e) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "Firebase token invalid/expired", "details", e.getMessage()));
    }
  }
}
