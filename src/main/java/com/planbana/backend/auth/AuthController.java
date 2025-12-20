package com.planbana.backend.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.planbana.backend.audit.*;
import com.planbana.backend.auth.dto.AuthDtos;
import com.planbana.backend.security.JwtService;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final JwtService jwt;
  private final AuditLogger auditLogger;

  // ⭐ Single source of truth for admin phone (normalized format)
  private static final String ADMIN_PHONE = "919999999999";

  public AuthController(
      UserRepository users,
      PasswordEncoder encoder,
      JwtService jwt,
      AuditLogger auditLogger) {
    this.users = users;
    this.encoder = encoder;
    this.jwt = jwt;
    this.auditLogger = auditLogger;
  }

  // Utility: normalize phone
  private static String normalizePhone(String phone) {
    if (phone == null)
      return null;
    return phone.trim().replaceAll("\\s+", "").replaceAll("[^0-9]", "");
  }

  // ---------------------------------------------------------
  // 1️⃣ CHECK PHONE
  // ---------------------------------------------------------
  @PostMapping("/check-phone")
  public ResponseEntity<?> checkPhone(@Valid @RequestBody AuthDtos.CheckPhoneRequest req) {
    try {
      FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(req.firebaseIdToken);
      String firebaseUid = decoded.getUid();

      String phone = normalizePhone(FirebaseAuth.getInstance().getUser(firebaseUid).getPhoneNumber());
      if (phone == null) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid Firebase token, phone missing"));
      }

      Optional<User> existing = users.findByPhone(phone);

      // Log phone check
      auditLogger.log(
          AuditCategory.USER_ACCOUNT,
          AuditAction.USER_PHONE_UPDATED,
          existing.map(User::getId).orElse(firebaseUid),
          null,
          null,
          Map.of("checkedPhone", phone));

      if (existing.isPresent()) {
        return ResponseEntity.ok(Map.of("status", "LOGIN_REQUIRED", "phone", phone));
      } else {
        return ResponseEntity.ok(Map.of("status", "REGISTER_REQUIRED", "phone", phone, "firebaseUid", firebaseUid));
      }

    } catch (Exception e) {
      auditLogger.log(
          AuditCategory.SECURITY_AUTH,
          AuditAction.INVALID_TOKEN_ATTEMPT,
          "UNKNOWN",
          null,
          null,
          Map.of("details", e.getMessage()));

      return ResponseEntity.badRequest()
          .body(Map.of("error", "Firebase token invalid", "details", e.getMessage()));
    }
  }

  // ---------------------------------------------------------
  // 2️⃣ REGISTER
  // ---------------------------------------------------------
  @PostMapping("/register-minimal")
  public ResponseEntity<?> registerMinimal(
      @Valid @RequestBody AuthDtos.RegisterMinimalRequest req,
      HttpServletResponse res) {

    try {
      FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(req.firebaseIdToken);
      String firebaseUid = decoded.getUid();

      String phone = normalizePhone(FirebaseAuth.getInstance().getUser(firebaseUid).getPhoneNumber());
      if (phone == null) {
        return ResponseEntity.badRequest().body(Map.of("error", "Firebase phone missing"));
      }

      if (users.findByPhone(phone).isPresent()) {
        return ResponseEntity.badRequest().body(Map.of("error", "Phone already registered"));
      }

      User u = new User();
      u.setFirebaseUid(firebaseUid);
      u.setPhone(phone);
      u.setPasswordHash(encoder.encode(req.password));
      u.setPhoneVerified(true);

      // ⭐ If this is the admin phone, give ADMIN + USER
      if (ADMIN_PHONE.equals(phone)) {
        u.setRoles(Set.of("ADMIN", "USER"));
      } else {
        u.setRoles(Set.of("USER"));
      }

      if (u.getLanguages() == null || u.getLanguages().isEmpty()) {
        u.setLanguages(List.of("English"));
      }

      users.save(u);

      // Log registration
      auditLogger.log(
          AuditCategory.USER_ACCOUNT,
          AuditAction.USER_REGISTERED,
          u.getId(),
          null,
          null,
          Map.of("phone", phone));

      // Generate JWT
      // Set<String> jwtRoles = u.getRoles().stream()
      // .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
      // .collect(Collectors.toSet());

      Set<String> jwtRoles = u.getRoles().stream()
          .map(r -> r.replace("ROLE_", "")) // normalize
          .map(r -> "ROLE_" + r) // standardize
          .collect(Collectors.toSet());

      String access = jwt.generateAccess(
          u.getPhone(),
          jwtRoles,
          u.getTokenVersion());

      String refresh = jwt.generateRefresh(u.getPhone());

      Cookie cookie = new Cookie("access_token", access);
      cookie.setPath("/");
      cookie.setHttpOnly(true);
      cookie.setSecure(false);
      cookie.setAttribute("SameSite", "None");
      res.addCookie(cookie);

      return ResponseEntity.ok(Map.of(
          "message", "Registered successfully",
          "accessToken", access,
          "refreshToken", refresh));

    } catch (Exception e) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "Registration failed", "details", e.getMessage()));
    }
  }

  // ---------------------------------------------------------
  // 3️⃣ REFRESH TOKEN
  // ---------------------------------------------------------
  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {

    String refresh = body.get("refreshToken");

    if (refresh == null || !jwt.validateToken(refresh)) {

      auditLogger.log(
          AuditCategory.SECURITY_AUTH,
          AuditAction.EXPIRED_TOKEN_USED,
          "UNKNOWN",
          null,
          null,
          Map.of("reason", "invalid_refresh_token"));

      return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired refresh token"));
    }

    String username = jwt.getUsername(refresh);
    User user = users.findByPhone(username)
        .orElseThrow(() -> new RuntimeException("User not found"));

    Set<String> roles = user.getRoles().stream()
        .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
        .collect(Collectors.toSet());

    return ResponseEntity.ok(Map.of(
        "accessToken", jwt.generateAccess(
            user.getPhone(),
            roles,
            user.getTokenVersion()),
        "refreshToken", jwt.generateRefresh(user.getPhone())));
  }

  // ---------------------------------------------------------
  // 4️⃣ LOGIN WITH FIREBASE (PASSWORD)
  // ---------------------------------------------------------
  @PostMapping("/login-firebase")
  public ResponseEntity<?> loginWithFirebase(
      @Valid @RequestBody AuthDtos.FirebaseLoginRequest req,
      HttpServletResponse res) {

    try {
      FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(req.firebaseIdToken);
      String firebaseUid = decoded.getUid();

      String phone = normalizePhone(FirebaseAuth.getInstance().getUser(firebaseUid).getPhoneNumber());
      if (phone == null) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid Firebase phone"));
      }

      Optional<User> existing = users.findByPhone(phone);
      if (existing.isEmpty()) {
        return ResponseEntity.status(404).body(Map.of("error", "User not found"));
      }

      User u = existing.get();

      if (Boolean.TRUE.equals(u.getDisabled())) {
        auditLogger.log(
            AuditCategory.USER_ACCOUNT,
            AuditAction.USER_ACCOUNT_DISABLED,
            u.getId(),
            null,
            null,
            Map.of("attempt", "login_attempt_on_disabled_account"));

        return ResponseEntity.status(403).body(Map.of(
            "error", "ACCOUNT_BLOCKED",
            "message", "Your account has been blocked. Please contact the administrator."));

      }

      if (!encoder.matches(req.password, u.getPasswordHash())) {

        auditLogger.log(
            AuditCategory.SECURITY_AUTH,
            AuditAction.MULTIPLE_FAILED_LOGIN_ATTEMPTS,
            u.getId(),
            null,
            null,
            Map.of("phone", phone));

        return ResponseEntity.badRequest().body(Map.of("error", "Invalid password"));
      }

      // ⭐ Ensure admin always has ADMIN + USER roles at login time
      if (ADMIN_PHONE.equals(u.getPhone())) {
        Set<String> fixedRoles = new HashSet<>(u.getRoles() != null ? u.getRoles() : Set.of());
        fixedRoles.add("ADMIN");
        fixedRoles.add("USER");
        u.setRoles(fixedRoles);
        users.save(u); // persist correction so DB matches
      }

      // Successful login
      auditLogger.log(
          AuditCategory.USER_ACCOUNT,
          AuditAction.USER_LOGGED_IN,
          u.getId(),
          null,
          null,
          Map.of("phone", phone));

      // Build JWT from roles stored on user (now guaranteed correct for admin)
      // Set<String> jwtRoles = u.getRoles().stream()
      // .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
      // .collect(Collectors.toSet());

      Set<String> jwtRoles = u.getRoles().stream()
          .map(r -> r.replace("ROLE_", "")) // normalize
          .map(r -> "ROLE_" + r) // standardize
          .collect(Collectors.toSet());

      String access = jwt.generateAccess(
          u.getPhone(),
          jwtRoles,
          u.getTokenVersion());

      String refresh = jwt.generateRefresh(u.getPhone());

      Cookie cookie = new Cookie("access_token", access);
      cookie.setHttpOnly(true);
      cookie.setSecure(true);
      cookie.setPath("/");
      cookie.setAttribute("SameSite", "Lax");
      res.addCookie(cookie);

      return ResponseEntity.ok(Map.of(
          "message", "Login successful",
          "accessToken", access,
          "refreshToken", refresh));

    } catch (Exception e) {

      auditLogger.log(
          AuditCategory.SECURITY_AUTH,
          AuditAction.INVALID_TOKEN_ATTEMPT,
          "UNKNOWN",
          null,
          null,
          Map.of("error", "firebase_login_failure"));

      return ResponseEntity.badRequest()
          .body(Map.of("error", "Invalid token", "details", e.getMessage()));
    }
  }

  // ---------------------------------------------------------
  // 5️⃣ LOGOUT
  // ---------------------------------------------------------
  @PostMapping("/logout")
  public Map<String, String> logout(Authentication auth) {

    if (auth != null) {
      String phone = auth.getName();
      String userId = users.findByPhone(phone).map(User::getId).orElse("UNKNOWN");

      auditLogger.log(
          AuditCategory.USER_ACCOUNT,
          AuditAction.USER_LOGGED_OUT,
          userId,
          null,
          null,
          Map.of("message", "User logged out"));
    }

    return Map.of("message", "Logged out");
  }

  // ---------------------------------------------------------
  // 6️⃣ LOGIN STATUS CHECK And Quick logout for BLOCKED USERS
  // ---------------------------------------------------------

  @PostMapping("/login-status")
  public ResponseEntity<?> loginStatus(@RequestBody AuthDtos.LoginStatusRequest req) {

    String phone = normalizePhone(req.phone);
    if (phone == null || phone.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("status", "INVALID"));
    }

    Optional<User> userOpt = users.findByPhone(phone);

    if (userOpt.isEmpty()) {
      return ResponseEntity.ok(Map.of("status", "NOT_FOUND"));
    }

    User user = userOpt.get();

    // 🚨 ADMIN IS NEVER BLOCKED
    if (ADMIN_PHONE.equals(user.getPhone())) {
      return ResponseEntity.ok(Map.of("status", "ACTIVE"));
    }

    // 🚫 BLOCKED USER
    if (Boolean.TRUE.equals(user.getDisabled())) {
      return ResponseEntity.status(403).body(Map.of(
          "status", "BLOCKED",
          "message", "Your account has been blocked. Please contact the administrator."));
    }

    return ResponseEntity.ok(Map.of("status", "ACTIVE"));
  }

}
