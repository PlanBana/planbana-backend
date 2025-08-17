package com.planbana.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthDtos {
  public static class RegisterRequest {
    @Email public String email;
    @NotBlank public String password;
    public String displayName;
  }
  public static class LoginRequest {
    @Email public String email;
    @NotBlank public String password;
  }
  public static class RefreshRequest {
    @NotBlank public String refreshToken;
  }
  public static class RequestPasswordReset {
    @Email public String email;
  }
  public static class ResetPassword {
    @NotBlank public String token;
    @NotBlank public String newPassword;
  }
}
