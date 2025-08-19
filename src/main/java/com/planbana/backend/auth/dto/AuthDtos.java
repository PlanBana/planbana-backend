package com.planbana.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public class AuthDtos {

  public static class RegisterRequest {
    @Pattern(regexp = "^[0-9]{8,15}$", message = "Phone number must be 8 to 15 digits")
    @NotBlank public String phone;

    @NotBlank public String password;

    @NotBlank public String displayName;
    public String email;
    public String avatarUrl;
    public String gender;
    public LocalDate birthDate;
    public String occupation;
    public List<String> languages;
    public Set<String> hobbies;
    public Double latitude;
    public Double longitude;
    public String city;
  }

  public static class LoginRequest {
    @NotBlank
    @Pattern(regexp = "^[0-9]{8,15}$", message = "Phone number must be 8 to 15 digits")
    public String phone;

    @NotBlank public String password;
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
