package com.planbana.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class AuthDtos {

  // PART 1 registration payload (minimal)
  public static class RegisterMinimalRequest {
    @Pattern(regexp = "^[0-9]{8,15}$", message = "Phone number must be 8 to 15 digits")
    @NotBlank public String phone;

    @NotBlank public String password;

    @NotBlank public String displayName;

    // one-time ticket returned by /otp/verify (purpose=register), 24h TTL
    @NotBlank public String registrationTicket;
  }

  public static class LoginRequest {
    @NotBlank
    @Pattern(regexp = "^[0-9]{8,15}$", message = "Phone number must be 8 to 15 digits")
    public String phone;

    @NotBlank public String password;
  }

  public static class LoginVerifyOtp {
    @NotBlank
    @Pattern(regexp = "^[0-9]{8,15}$", message = "Phone number must be 8 to 15 digits")
    public String phone;

    @NotBlank
    @Pattern(regexp = "^[0-9]{4,8}$", message = "OTP must be 4 to 8 digits")
    public String otp;
  }

  public static class RefreshRequest {
    @NotBlank public String refreshToken;
  }

  public static class RequestPasswordReset {
    @NotBlank
    @Pattern(regexp = "^[0-9]{8,15}$", message = "Phone number must be 8 to 15 digits")
    public String phone;
  }

  public static class ResetPassword {
    @NotBlank public String token;
    @NotBlank public String newPassword;
  }
}
