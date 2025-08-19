package com.planbana.backend.auth;

import com.planbana.backend.auth.dto.AuthDtos;
import com.planbana.backend.security.JwtService;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final AuthenticationManager authManager;
  private final JwtService jwt;
  private final VerificationTokenRepository verifyRepo;
  private final PasswordResetTokenRepository resetRepo;
  private final MailService mailService;

  public AuthController(UserRepository users, PasswordEncoder encoder, AuthenticationManager authManager,
                        JwtService jwt, VerificationTokenRepository verifyRepo,
                        PasswordResetTokenRepository resetRepo, MailService mailService) {
    this.users = users;
    this.encoder = encoder;
    this.authManager = authManager;
    this.jwt = jwt;
    this.verifyRepo = verifyRepo;
    this.resetRepo = resetRepo;
    this.mailService = mailService;
  }

  @PostMapping("/register")
  public ResponseEntity<?> register(@Valid @RequestBody AuthDtos.RegisterRequest req) {
    if (users.findByPhone(req.phone).isPresent()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Phone already registered"));
    }

    if (req.email != null && users.findByEmail(req.email.toLowerCase()).isPresent()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Email already registered"));
    }

    User u = new User();
    u.setPhone(req.phone);
    u.setPasswordHash(encoder.encode(req.password));
    u.setDisplayName(req.displayName);

    // Optional fields
    if (req.email != null) u.setEmail(req.email.toLowerCase());
    if (req.avatarUrl != null) u.setAvatarUrl(req.avatarUrl);
    if (req.gender != null) u.setGender(req.gender);
    if (req.birthDate != null) u.setBirthDate(req.birthDate);
    if (req.occupation != null) u.setOccupation(req.occupation);
    if (req.languages != null && !req.languages.isEmpty()) u.setLanguages(req.languages);
    else u.setLanguages(List.of("English"));
    if (req.hobbies != null) u.setHobbies(req.hobbies);
    if (req.latitude != null) u.setLatitude(req.latitude);
    if (req.longitude != null) u.setLongitude(req.longitude);
    if (req.city != null) u.setCity(req.city);

    u.setRoles(Set.of("USER"));
    u.setPhoneVerified(true); // Assuming phone was verified before registration

    users.save(u);

    // Optional email verification
    if (u.getEmail() != null) {
      String token = UUID.randomUUID().toString();
      verifyRepo.save(new VerificationToken(u.getId(), token, Instant.now().plus(24, ChronoUnit.HOURS)));
      mailService.sendSimple(u.getEmail(), "Verify your email", "Click to verify: /verify-email?token=" + token);
    }

    return ResponseEntity.ok(Map.of("message", "Registered successfully"));
  }

  @GetMapping("/verify-email")
  public ResponseEntity<?> verifyEmail(@RequestParam String token) {
    return verifyRepo.findByToken(token).map(v -> {
      if (v.getExpiresAt().isBefore(Instant.now())) {
        return ResponseEntity.badRequest().body(Map.of("error", "Token expired"));
      }
      User u = users.findById(v.getUserId()).orElseThrow();
      u.setEmailVerified(true);
      users.save(u);
      verifyRepo.delete(v);
      return ResponseEntity.ok(Map.of("message", "Email verified."));
    }).orElse(ResponseEntity.badRequest().body(Map.of("error", "Invalid token")));
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@Valid @RequestBody AuthDtos.LoginRequest req, HttpServletResponse res) {
    authManager.authenticate(new UsernamePasswordAuthenticationToken(req.phone, req.password));
    User u = users.findByPhone(req.phone).orElseThrow();

    String access = jwt.generateAccess(u.getPhone(), Map.of("roles", u.getRoles()));
    String refresh = jwt.generateRefresh(u.getPhone());

    Cookie cookie = new Cookie("access_token", access);
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    res.addCookie(cookie);

    return ResponseEntity.ok(Map.of("refreshToken", refresh));
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(@RequestBody AuthDtos.RefreshRequest req, HttpServletResponse res) {
    if (!jwt.validateToken(req.refreshToken)) {
      return ResponseEntity.badRequest().body(Map.of("error", "Invalid refresh token"));
    }

    String phone = jwt.getUsername(req.refreshToken);
    User u = users.findByPhone(phone).orElseThrow();

    String access = jwt.generateAccess(u.getPhone(), Map.of("roles", u.getRoles()));

    Cookie cookie = new Cookie("access_token", access);
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    res.addCookie(cookie);

    return ResponseEntity.ok(Map.of("message", "refreshed"));
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(HttpServletResponse res) {
    Cookie cookie = new Cookie("access_token", "");
    cookie.setPath("/");
    cookie.setMaxAge(0);
    res.addCookie(cookie);
    return ResponseEntity.ok(Map.of("message", "logged out"));
  }

  @PostMapping("/request-password-reset")
  public ResponseEntity<?> requestPasswordReset(@RequestBody @Valid AuthDtos.RequestPasswordReset req) {
    return users.findByPhone(req.phone).map(u -> {
      String token = UUID.randomUUID().toString();
      resetRepo.save(new PasswordResetToken(u.getId(), token, Instant.now().plus(1, ChronoUnit.HOURS)));
      if (u.getEmail() != null) {
        mailService.sendSimple(u.getEmail(), "Password reset", "Reset link: /reset-password?token=" + token);
      }
      return ResponseEntity.ok(Map.of("message", "If your phone exists, a reset link has been sent."));
    }).orElse(ResponseEntity.ok(Map.of("message", "If your phone exists, a reset link has been sent.")));
  }

  @PostMapping("/reset-password")
  public ResponseEntity<?> resetPassword(@RequestBody @Valid AuthDtos.ResetPassword req) {
    return resetRepo.findByToken(req.token).map(t -> {
      if (t.getExpiresAt().isBefore(Instant.now())) {
        return ResponseEntity.badRequest().body(Map.of("error", "Token expired"));
      }
      var user = users.findById(t.getUserId()).orElseThrow();
      user.setPasswordHash(encoder.encode(req.newPassword));
      users.save(user);
      resetRepo.delete(t);
      return ResponseEntity.ok(Map.of("message", "Password updated"));
    }).orElse(ResponseEntity.badRequest().body(Map.of("error", "Invalid token")));
  }
}
