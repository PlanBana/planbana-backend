package com.planbana.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {

  public static class RegisterMinimalRequest {
    @NotBlank
    public String firebaseIdToken;

    @NotBlank
    @Size(min = 8, max = 100)
    public String password;
  }

  public static class FirebaseLoginRequest {
    @NotBlank
    public String firebaseIdToken;

    @NotBlank
    @Size(min = 8, max = 100)
    public String password;
  }

  public static class CheckPhoneRequest {
    @NotBlank
    public String firebaseIdToken;
  }

  public static class RefreshRequest {
    @NotBlank
    public String refreshToken;
  }

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

  // AuthDtos.java
  public static class LoginStatusRequest {
    public String phone;
  }

  public static class LoginStatusResponse {
    public String status; // ACTIVE | BLOCKED | NOT_FOUND
    public String message; // optional
  }

}
