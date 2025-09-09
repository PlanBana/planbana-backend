package com.planbana.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

  private static final Logger log = LoggerFactory.getLogger(OtpService.class);

  // Purpose-aware OTP store: key = phone|purpose
  private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

  // Registration tickets (one-time), in-memory
  // key = ticket UUID -> value = (phone, expiry)
  private final Map<String, RegistrationTicket> registrationTickets = new ConcurrentHashMap<>();

  private final SecureRandom random = new SecureRandom();

  public static final int OTP_TTL_SECONDS = 300;                // 5 mins
  public static final int RESEND_COOLDOWN_SECONDS = 30;          // cooldown
  public static final int MAX_ATTEMPTS = 5;
  public static final int REG_TICKET_TTL_SECONDS = 24 * 60 * 60; // 24h

  // ------------ phone normalization (basic digits-only) ------------
  private static String normalizePhone(String phone) {
    if (phone == null) return null;
    return phone.trim().replaceAll("\\s+", "").replaceAll("[^0-9]", "");
  }

  private String key(String rawPhone, String purpose) {
    String phone = normalizePhone(rawPhone);
    return phone + "|" + purpose;
  }

  // ---------------- OTP ----------------
  /** Generate/return OTP so controllers can expose it in dev/local. */
  public String sendOtp(String rawPhone, String purpose) {
    String phone = normalizePhone(rawPhone);
    String k = key(phone, purpose);
    OtpEntry existing = otpStore.get(k);
    Instant now = Instant.now();

    if (existing != null && now.isBefore(existing.sentAt.plusSeconds(RESEND_COOLDOWN_SECONDS))) {
      // Return same code during cooldown
      log.info("sendOtp cooldown active for {} / {}", phone, purpose);
      return existing.code;
    }

    String code = String.format("%06d", random.nextInt(1_000_000));
    otpStore.put(k, new OtpEntry(code, now.plusSeconds(OTP_TTL_SECONDS), now, 0));

    // INFO so you can see it in console during dev. Do NOT keep at INFO in production.
    log.info("DEV OTP {} for {} is {}", purpose, phone, code);
    return code;
  }

  public boolean verifyOtp(String rawPhone, String purpose, String otp) {
    String phone = normalizePhone(rawPhone);
    String k = key(phone, purpose);
    OtpEntry entry = otpStore.get(k);
    Instant now = Instant.now();

    if (entry == null) {
      return false;
    }
    if (entry.expiry.isBefore(now)) {
      otpStore.remove(k);
      return false;
    }
    if (entry.attempts >= MAX_ATTEMPTS) {
      otpStore.remove(k);
      return false;
    }

    boolean valid = entry.code.equals(otp);
    if (!valid) {
      entry.attempts++;
      return false;
    }

    otpStore.remove(k);
    return true;
  }

  // ---------------- Registration Tickets ----------------
  public String issueRegistrationTicket(String rawPhone) {
    String phone = normalizePhone(rawPhone);
    String ticket = UUID.randomUUID().toString();
    registrationTickets.put(ticket, new RegistrationTicket(phone, Instant.now().plusSeconds(REG_TICKET_TTL_SECONDS)));
    return ticket;
  }

  public ConsumeResult consumeRegistrationTicket(String ticket, String rawPhone) {
    String phone = normalizePhone(rawPhone);
    RegistrationTicket rt = registrationTickets.remove(ticket);
    if (rt == null) {
      return ConsumeResult.NOT_FOUND;
    }
    if (!rt.phone.equals(phone)) {
      return ConsumeResult.PHONE_MISMATCH;
    }
    if (rt.expiry.isBefore(Instant.now())) {
      return ConsumeResult.EXPIRED;
    }
    return ConsumeResult.OK;
  }

  public enum ConsumeResult { OK, NOT_FOUND, PHONE_MISMATCH, EXPIRED }

  // ---------------- Internal types ----------------
  private static class OtpEntry {
    final String code;
    final Instant expiry;
    final Instant sentAt;
    int attempts;
    OtpEntry(String code, Instant expiry, Instant sentAt, int attempts) {
      this.code = code;
      this.expiry = expiry;
      this.sentAt = sentAt;
      this.attempts = attempts;
    }
  }

  private static class RegistrationTicket {
    final String phone;
    final Instant expiry;
    RegistrationTicket(String phone, Instant expiry) {
      this.phone = phone;
      this.expiry = expiry;
    }
  }
}
