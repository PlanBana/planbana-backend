package com.planbana.backend.auth;

import com.planbana.backend.auth.dto.AuthDtos;
import com.planbana.backend.security.JwtService;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
  private final Environment env;

  public AuthController(UserRepository users,
                        PasswordEncoder encoder,
                        AuthenticationManager authManager,
                        JwtService jwt,
                        PasswordResetTokenRepository resetRepo,
                        MailService mailService,
                        OtpService otpService,
                        Environment env) {
    this.users = users;
    this.encoder = encoder;
    this.authManager = authManager;
    this.jwt = jwt;
    this.resetRepo = resetRepo;
    this.mailService = mailService;
    this.otpService = otpService;
    this.env = env;
  }

  // -------------------------------------------------------
  // Helpers
  // -------------------------------------------------------
  private static String normalizePhone(String phone) {
    if (phone == null) return null;
    return phone.trim().replaceAll("\\s+", "").replaceAll("[^0-9]", "");
  }

  private boolean isDevProfile() {
    return env != null && env.acceptsProfiles(Profiles.of("dev", "local"));
  }

  // -------------------------------------------------------
  // OTP (purpose-aware)
  // -------------------------------------------------------

  /**
   * Request an OTP for a specific purpose.
   * - purpose = register : allowed only if phone is NOT registered
   * - purpose = login/reset : allowed only if phone IS registered
   */
  @PostMapping("/otp/request")
  public ResponseEntity<?> requestOtp(@RequestBody Map<String, String> payload) {
    String phone = normalizePhone(Optional.ofNullable(payload.get("phone")).orElse(""));
    String purpose = Optional.ofNullable(payload.get("purpose")).orElse("register")
        .trim().toLowerCase(Locale.ROOT);

    if (phone == null || phone.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "phone is required"));
    }
    if (!Set.of("register", "login", "reset").contains(purpose)) {
      return ResponseEntity.badRequest().body(Map.of("error", "purpose must be one of: register, login, reset"));
    }

    boolean exists = users.findByPhone(phone).isPresent();
    if ("register".equals(purpose) && exists) {
      return ResponseEntity.badRequest().body(Map.of("error", "Phone already registered"));
    }
    if ((("login".equals(purpose)) || ("reset".equals(purpose))) && !exists) {
      return ResponseEntity.badRequest().body(Map.of("error", "Phone not registered"));
    }

    String otp = otpService.sendOtp(phone, purpose);

    if (isDevProfile()) {
      // Expose OTP only in dev/local profiles for convenience
      return ResponseEntity.ok(Map.of("message", "OTP sent", "devOtp", otp));
    }
    return ResponseEntity.ok(Map.of("message", "OTP sent"));
  }

  /**
   * Verify OTP.
   * For purpose=register, returns a 24h registration ticket (one-time).
  */
  @PostMapping("/otp/verify")
  public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> payload) {
    String phone = normalizePhone(Optional.ofNullable(payload.get("phone")).orElse(""));
    String purpose = Optional.ofNullable(payload.get("purpose")).orElse("register")
        .trim().toLowerCase(Locale.ROOT);
    String otp = Optional.ofNullable(payload.get("otp")).orElse("").trim();

    if (phone == null || phone.isEmpty() || otp.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "phone and otp are required"));
    }
    if (!Set.of("register", "login", "reset").contains(purpose)) {
      return ResponseEntity.badRequest().body(Map.of("error", "purpose must be one of: register, login, reset"));
    }

    if (!otpService.verifyOtp(phone, purpose, otp)) {
      return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired OTP"));
    }

    if ("register".equals(purpose)) {
      String registrationTicket = otpService.issueRegistrationTicket(phone); // 24h TTL
      return ResponseEntity.ok(Map.of(
          "registrationTicket", registrationTicket,
          "expiresInSeconds", OtpService.REG_TICKET_TTL_SECONDS
      ));
    }

    return ResponseEntity.ok(Map.of("message", "otp verified"));
  }

  // -------------------------------------------------------
  // Registration (PART 1): minimal account with ticket
  // Auto-login: issue tokens immediately after creating the user
  // -------------------------------------------------------

  /**
   * Create the account (phone/password/displayName) using a one-time registration ticket.
   * After this, the user is **already authenticated** (access cookie set, refreshToken returned)
   * and can go straight to onboarding to complete profile via /api/users/me.
   */
  @PostMapping("/register-minimal")
  public ResponseEntity<?> registerMinimal(@Valid @RequestBody AuthDtos.RegisterMinimalRequest req,
                                           HttpServletResponse res) {
    String phone = normalizePhone(req.phone);

    OtpService.ConsumeResult r = otpService.consumeRegistrationTicket(req.registrationTicket, phone);
    if (r != OtpService.ConsumeResult.OK) {
      String msg = switch (r) {
        case NOT_FOUND -> "Registration ticket not found (server restarted or wrong ticket)";
        case PHONE_MISMATCH -> "Registration ticket does not match phone";
        case EXPIRED -> "Registration ticket expired";
        default -> "Phone verification required";
      };
      return ResponseEntity.badRequest().body(Map.of("error", msg, "reason", r.name()));
    }

    if (users.findByPhone(phone).isPresent()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Phone already registered"));
    }

    // Create user (minimal)
    User u = new User();
    u.setPhone(phone);
    u.setPasswordHash(encoder.encode(req.password));
    u.setDisplayName(req.displayName);
    u.setPhoneVerified(true);
    u.setRoles(Set.of("USER"));
    if (u.getLanguages() == null || u.getLanguages().isEmpty()) {
      u.setLanguages(List.of("English"));
    }
    users.save(u);

    // --- AUTO-LOGIN HERE ---
    String access = jwt.generateAccess(u.getPhone(), Map.of("roles", u.getRoles()));
    String refresh = jwt.generateRefresh(u.getPhone());

    Cookie cookie = new Cookie("access_token", access);
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    cookie.setAttribute("SameSite", "Lax");
    res.addCookie(cookie);

    Map<String, Object> userPayload = Map.of(
        "id", u.getId(),
        "phone", u.getPhone(),
        "displayName", u.getDisplayName(),
        "phoneVerified", u.isPhoneVerified(),
        "roles", u.getRoles()
    );

    return ResponseEntity.ok(Map.of(
        "message", "Registered successfully. You are now logged in.",
        "refreshToken", refresh,
        "user", userPayload
    ));
  }

  // -------------------------------------------------------
  // LOGIN (for returning users)
  // -------------------------------------------------------
  @PostMapping("/login")
  public ResponseEntity<?> login(@Valid @RequestBody AuthDtos.LoginRequest req) {
    String phone = normalizePhone(req.phone);

    authManager.authenticate(new UsernamePasswordAuthenticationToken(phone, req.password));

    User u = users.findByPhone(phone).orElseThrow();
    if (!u.isPhoneVerified()) {
      return ResponseEntity.status(403).body(Map.of("error", "Phone not verified. Please verify your account first."));
    }

    String otp = otpService.sendOtp(phone, "login");
    if (isDevProfile()) {
      return ResponseEntity.ok(Map.of("message", "OTP sent for 2FA. Verify to complete login.", "devOtp", otp));
    }
    return ResponseEntity.ok(Map.of("message", "OTP sent for 2FA. Verify to complete login."));
  }

  @PostMapping("/login-verify-otp")
  public ResponseEntity<?> loginVerifyOtp(@RequestBody AuthDtos.LoginVerifyOtp req, HttpServletResponse res) {
    String phone = normalizePhone(req.phone);

    if (!otpService.verifyOtp(phone, "login", req.otp)) {
      return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired OTP"));
    }

    User u = users.findByPhone(phone).orElseThrow();

    String access = jwt.generateAccess(u.getPhone(), Map.of("roles", u.getRoles()));
    String refresh = jwt.generateRefresh(u.getPhone());

    Cookie cookie = new Cookie("access_token", access);
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    cookie.setAttribute("SameSite", "Lax");
    res.addCookie(cookie);

    return ResponseEntity.ok(Map.of("refreshToken", refresh));
  }

  // -------------------------------------------------------
  // Token refresh / logout
  // -------------------------------------------------------
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

  // -------------------------------------------------------
  // Password reset (email if present)
  // -------------------------------------------------------
  @PostMapping("/request-password-reset")
  public ResponseEntity<?> requestPasswordReset(@RequestBody @Valid AuthDtos.RequestPasswordReset req) {
    String phone = normalizePhone(req.phone);

    return users.findByPhone(phone).map(u -> {
      String token = UUID.randomUUID().toString();
      resetRepo.save(new PasswordResetToken(u.getId(), token, java.time.Instant.now().plus(1, ChronoUnit.HOURS)));
      if (u.getEmail() != null) {
        mailService.sendSimple(u.getEmail(), "Password reset", "Reset link: /reset-password?token=" + token);
      }
      return ResponseEntity.ok(Map.of("message", "If your phone exists, a reset link has been sent."));
    }).orElse(ResponseEntity.ok(Map.of("message", "If your phone exists, a reset link has been sent.")));
  }

  @PostMapping("/reset-password")
  public ResponseEntity<?> resetPassword(@RequestBody @Valid AuthDtos.ResetPassword req) {
    return resetRepo.findByToken(req.token).map(t -> {
      if (t.getExpiresAt().isBefore(java.time.Instant.now())) {
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
