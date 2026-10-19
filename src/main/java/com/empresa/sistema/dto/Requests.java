package com.empresa.sistema.dto;

import com.empresa.sistema.entity.Role;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class Requests {
  private Requests() {}

  public record Login(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {}

  public record AnalyticsUnlock(@NotBlank @Size(max = 72) String password) {}

  public record UserCreate(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String password,
      @Size(min = 12, max = 72) String analyticsPassword,
      @NotNull Role role) {}

  public record UserUpdate(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @Size(min = 12, max = 72) String password,
      @Size(min = 12, max = 72) String analyticsPassword,
      @NotNull Role role,
      boolean active) {}

  public record Screw(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Size(max = 1000) String description,
      @Pattern(regexp = "^https?://[^\\s]+$", message = "deve ser URL HTTP(S)") @Size(max = 1000)
          String photoUrl,
      @Size(max = 2000) String specifications) {}

  public record DeviceCreate(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String deviceId,
      @NotBlank @Size(max = 120) String name) {}

  public record BatchCreate(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String operationId,
      @Positive @Max(1000000000) long target,
      @NotNull @Positive Long screwTypeId,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String deviceId) {}

  public record Event(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String deviceId,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String operationId,
      @NotNull UUID eventId,
      @NotNull Instant timestamp) {}
}
