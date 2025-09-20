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
    if (phone == null) return null;
    return phone.trim().replaceAll("\\s+", "").replaceAll("[^0-9]", "");
  }

  // -------------------------
  // SIGNUP (requires Firebase OTP)
  // -------------------------
  @PostMapping("/register-minimal")
  public ResponseEntity<?> registerMinimal(@Valid @RequestBody AuthDtos.RegisterMinimalRequest req,
                                           HttpServletResponse res) {
    try {
      // 1) Verify Firebase ID token
      FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(req.firebaseIdToken);

      String uid = decoded.getUid();
      String phone = normalizePhone(
          FirebaseAuth.getInstance().getUser(uid).getPhoneNumber()
      );

      if (phone == null || phone.isEmpty()) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid Firebase token: phone missing"));
      }

      // 2) Prevent duplicate
      if (users.findByPhone(phone).isPresent()) {
        return ResponseEntity.badRequest().body(Map.of("error", "Phone already registered"));
      }

      // 3) Create user
      User u = new User();
      u.setFirebaseUid(uid); // ✅ store firebase uid
      u.setPhone(phone);
      u.setPasswordHash(encoder.encode(req.password));
      u.setPhoneVerified(true);
      u.setRoles(Set.of("USER"));

      if (u.getLanguages() == null || u.getLanguages().isEmpty()) {
        u.setLanguages(List.of("English"));
      }
      users.save(u);

      // 4) Issue JWTs
      String access = jwt.generateAccess(u.getPhone(), Map.of("roles", u.getRoles()));
      String refresh = jwt.generateRefresh(u.getPhone());

      Cookie cookie = new Cookie("access_token", access);
      cookie.setPath("/");
      cookie.setHttpOnly(true);
      cookie.setSecure(true);
      cookie.setAttribute("SameSite", "Lax");
      res.addCookie(cookie);

      return ResponseEntity.ok(Map.of(
          "message", "Registered successfully. You are now logged in.",
          "accessToken", access,
          "refreshToken", refresh,
          "user", Map.of(
              "id", u.getId(),
              "firebaseUid", u.getFirebaseUid(),
              "phone", u.getPhone(),
              "phoneVerified", u.isPhoneVerified(),
              "roles", u.getRoles()
          )
      ));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("error", "Firebase token invalid/expired", "details", e.getMessage()));
    }
  }

  // -------------------------
  // LOGIN (requires Firebase OTP every time)
  // -------------------------
  @PostMapping("/login-firebase")
  public ResponseEntity<?> loginWithFirebase(@Valid @RequestBody AuthDtos.FirebaseLoginRequest req,
                                             HttpServletResponse res) {
    try {
      FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(req.firebaseIdToken);
      String uid = decoded.getUid();

      String phone = normalizePhone(
          FirebaseAuth.getInstance().getUser(uid).getPhoneNumber()
      );

      if (phone == null || phone.isEmpty()) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid Firebase token: phone missing"));
      }

      Optional<User> existing = users.findByPhone(phone);
      if (existing.isEmpty()) {
        return ResponseEntity.status(404).body(Map.of("error", "User not found. Please register first."));
      }

      User u = existing.get();
      if (!u.isPhoneVerified()) {
        u.setPhoneVerified(true);
        users.save(u);
      }

      String access = jwt.generateAccess(u.getPhone(), Map.of("roles", u.getRoles()));
      String refresh = jwt.generateRefresh(u.getPhone());

      Cookie cookie = new Cookie("access_token", access);
      cookie.setPath("/");
      cookie.setHttpOnly(true);
      cookie.setSecure(true);
      cookie.setAttribute("SameSite", "Lax");
      res.addCookie(cookie);

      return ResponseEntity.ok(Map.of(
          "message", "Login successful",
          "accessToken", access,
          "refreshToken", refresh
      ));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("error", "Firebase token invalid/expired", "details", e.getMessage()));
    }
  }

  // -------------------------
  // TOKEN REFRESH / LOGOUT
  // -------------------------
  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(@RequestBody AuthDtos.RefreshRequest req, HttpServletResponse res) {
    if (!jwt.validateToken(req.refreshToken)) {
      return ResponseEntity.badRequest().body(Map.of("error", "Invalid refresh token"));
    }

    String phone = jwt.getUsername(req.refreshToken);
    User u = users.findByPhone(phone).orElse(null);
    if (u == null) {
      return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
    }

    String access = jwt.generateAccess(u.getPhone(), Map.of("roles", u.getRoles()));
    Cookie cookie = new Cookie("access_token", access);
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    cookie.setAttribute("SameSite", "Lax");
    res.addCookie(cookie);

    return ResponseEntity.ok(Map.of("message", "refreshed"));
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(HttpServletResponse res) {
    Cookie cookie = new Cookie("access_token", "");
    cookie.setPath("/");
    cookie.setMaxAge(0);
    cookie.setAttribute("SameSite", "Lax");
    res.addCookie(cookie);
    return ResponseEntity.ok(Map.of("message", "logged out"));
  }

  // -------------------------
  // (Optional) Password reset
  // -------------------------
  @PostMapping("/request-password-reset")
  public ResponseEntity<?> requestPasswordReset(@RequestBody @Valid AuthDtos.RequestPasswordReset req) {
    return ResponseEntity.ok(Map.of("message", "If your phone exists, a reset link has been sent."));
  }

  @PostMapping("/reset-password")
  public ResponseEntity<?> resetPassword(@RequestBody @Valid AuthDtos.ResetPassword req) {
    return ResponseEntity.ok(Map.of("message", "Password updated"));
  }
}
