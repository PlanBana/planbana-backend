package com.planbana.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {

  // Signup using Firebase ID token (OTP already verified on client)
  public static class RegisterMinimalRequest {
    @NotBlank
    public String firebaseIdToken;   // from client after successful Firebase phone auth

    @NotBlank
    @Size(min = 8, max = 100)
    public String password;
  }

  // Login using Firebase ID token (OTP already verified on client)
  public static class FirebaseLoginRequest {
    @NotBlank
    public String firebaseIdToken;
  }

  // Refresh
  public static class RefreshRequest {
    @NotBlank
    public String refreshToken;
  }

  // Password reset — optional, left as-is if you use email/SMS
  public static class RequestPasswordReset {
    @NotBlank
    public String phone;
  }

  public static class ResetPassword {
    @NotBlank
    public String token;

    @NotBlank
    @Size(min = 8, max = 100)
    public String newPassword;
  }
}
