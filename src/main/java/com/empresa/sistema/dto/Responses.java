package com.empresa.sistema.dto;

import com.empresa.sistema.entity.*;
import java.time.Instant;
import java.util.UUID;

public final class Responses {
  private Responses() {}

  public record Token(String accessToken, String tokenType, long expiresIn) {}

  public record UserView(
      Long id,
      String name,
      String email,
      Role role,
      boolean active,
      boolean analyticsPasswordConfigured) {
    public static UserView of(User u) {
      return new UserView(
          u.getId(),
          u.getName(),
          u.getEmail(),
          u.getRole(),
          u.isActive(),
          u.getAnalyticsPasswordHash() != null);
    }
  }

  public record ScrewView(
      Long id,
      String name,
      String description,
      String photoUrl,
      String specifications,
      boolean active) {
    public static ScrewView of(ScrewType s) {
      return new ScrewView(
          s.getId(),
          s.getName(),
          s.getDescription(),
          s.getPhotoUrl(),
          s.getSpecifications(),
          s.isActive());
    }
  }

  public record BatchView(
      Long id,
      String operationId,
      long target,
      long currentCount,
      double progress,
      BatchStatus status,
      Long screwTypeId,
      String screwTypeName,
      Long operatorId,
      String deviceId,
      Instant createdAt,
      Instant startedAt,
      Instant finishedAt) {
    public static BatchView of(ProductionBatch b) {
      return new BatchView(
          b.getId(),
          b.getOperationId(),
          b.getTarget(),
          b.getCurrentCount(),
          100.0 * b.getCurrentCount() / b.getTarget(),
          b.getStatus(),
          b.getScrewType().getId(),
          b.getScrewType().getName(),
          b.getOperator().getId(),
          b.getDevice().getId(),
          b.getCreatedAt(),
          b.getStartedAt(),
          b.getFinishedAt());
    }
  }

  public record EventAccepted(
      boolean accepted, boolean duplicate, long currentCount, UUID eventId) {}

  public record EventView(
      UUID eventId, String deviceId, Instant detectedAt, Instant receivedAt, long countAfter) {
    public static EventView of(ProductionEvent e) {
      return new EventView(
          e.getEventId(),
          e.getDevice().getId(),
          e.getDetectedAt(),
          e.getReceivedAt(),
          e.getCountAfter());
    }
  }

  public record PauseView(
      Long id, Instant pausedAt, Instant resumedAt, Long pausedBy, Long resumedBy) {
    public static PauseView of(ProductionPause p) {
      return new PauseView(
          p.getId(),
          p.getPausedAt(),
          p.getResumedAt(),
          p.getPausedBy().getId(),
          p.getResumedBy() == null ? null : p.getResumedBy().getId());
    }
  }

  public record DeviceView(String deviceId, String name, boolean active) {
    public static DeviceView of(Device d) {
      return new DeviceView(d.getId(), d.getName(), d.isActive());
    }
  }

  public record DeviceKey(String deviceId, String apiKey) {}
}
