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
  private final PasswordResetTokenRepository resetRepo;
  private final MailService mailService;
  private final OtpService otpService;

  public AuthController(UserRepository users, PasswordEncoder encoder, AuthenticationManager authManager,
                        JwtService jwt, PasswordResetTokenRepository resetRepo,
                        MailService mailService, OtpService otpService) {
    this.users = users;
    this.encoder = encoder;
    this.authManager = authManager;
    this.jwt = jwt;
    this.resetRepo = resetRepo;
    this.mailService = mailService;
    this.otpService = otpService;
  }

  // ---------------------------
  // Registration (email optional; not stored unless OAuth later)
  // ---------------------------
  @PostMapping("/register")
  public ResponseEntity<?> register(@Valid @RequestBody AuthDtos.RegisterRequest req) {
    if (users.findByPhone(req.phone).isPresent()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Phone already registered"));
    }

    User u = new User();
    u.setPhone(req.phone);
    u.setPasswordHash(encoder.encode(req.password));
    u.setDisplayName(req.displayName);

    // Optional profile fields (email intentionally NOT set here; only via OAuth flow)
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
    u.setPhoneVerified(false); // initial: not verified until OTP

    users.save(u);

    // Send account verification OTP (separate from 2FA at login; same service)
    String otp = otpService.generateOtp(req.phone);
    System.out.println("REGISTER OTP for " + req.phone + ": " + otp);

    return ResponseEntity.ok(Map.of("message", "Registered. Please verify your phone via OTP."));
  }

  // ---------------------------
  // Account phone verification (one-time)
  // ---------------------------
  @PostMapping("/request-otp")
  public ResponseEntity<?> requestOtp(@RequestBody Map<String, String> payload) {
    String phone = payload.get("phone");
    Optional<User> userOpt = users.findByPhone(phone);
    if (userOpt.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Phone not registered"));
    }
    String otp = otpService.generateOtp(phone);
    System.out.println("ACCOUNT VERIFY OTP for " + phone + ": " + otp);
    return ResponseEntity.ok(Map.of("message", "OTP sent"));
  }

  @PostMapping("/verify-otp")
  public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> payload) {
    String phone = payload.get("phone");
    String otp = payload.get("otp");

    if (!otpService.verifyOtp(phone, otp)) {
      return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired OTP"));
    }

    User user = users.findByPhone(phone).orElseThrow();
    user.setPhoneVerified(true);
    users.save(user);

    return ResponseEntity.ok(Map.of("message", "Phone verified"));
  }

  // ---------------------------
  // LOGIN (Step 1: password check -> send 2FA OTP; no tokens yet)
  // ---------------------------
  @PostMapping("/login")
  public ResponseEntity<?> login(@Valid @RequestBody AuthDtos.LoginRequest req) {
    // 1) Password authentication
    authManager.authenticate(new UsernamePasswordAuthenticationToken(req.phone, req.password));

    // 2) Optional gate: require account to be phone-verified first
    User u = users.findByPhone(req.phone).orElseThrow();
    if (!u.isPhoneVerified()) {
      // You can force account verification before permitting login 2FA
      return ResponseEntity.status(403).body(Map.of("error", "Phone not verified. Please verify your account first."));
    }

    // 3) Generate 2FA OTP for login
    String otp = otpService.generateOtp(req.phone);
    System.out.println("LOGIN 2FA OTP for " + req.phone + ": " + otp);

    // 4) Tell client to call /login-verify-otp
    return ResponseEntity.ok(Map.of("message", "OTP sent for 2FA. Verify to complete login."));
  }

  // ---------------------------
  // LOGIN (Step 2: verify 2FA OTP -> issue tokens)
  // ---------------------------
  @PostMapping("/login-verify-otp")
  public ResponseEntity<?> loginVerifyOtp(@RequestBody AuthDtos.LoginVerifyOtp req, HttpServletResponse res) {
    if (!otpService.verifyOtp(req.phone, req.otp)) {
      return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired OTP"));
    }

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

  // ---------------------------
  // Token refresh / logout
  // ---------------------------
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

  // ---------------------------
  // Password reset (still by phone; email used only if present from OAuth)
  // ---------------------------
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
