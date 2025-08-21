
package com.planbana.backend.auth;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

  private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
  private final Random random = new Random();

  public String generateOtp(String phone) {
    String otp = String.format("%06d", random.nextInt(999999));
    otpStore.put(phone, new OtpEntry(otp, Instant.now().plusSeconds(300))); // 5 mins expiry
    return otp;
  }

  public boolean verifyOtp(String phone, String otp) {
    OtpEntry entry = otpStore.get(phone);
    if (entry == null || entry.expiry.isBefore(Instant.now())) {
      otpStore.remove(phone);
      return false;
    }

    boolean isValid = entry.code.equals(otp);
    if (isValid) {
      otpStore.remove(phone);
    }

    return isValid;
  }

  private static class OtpEntry {
    String code;
    Instant expiry;

    OtpEntry(String code, Instant expiry) {
      this.code = code;
      this.expiry = expiry;
    }
  }
}
