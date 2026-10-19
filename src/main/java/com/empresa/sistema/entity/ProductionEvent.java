package com.empresa.sistema.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "production_events")
public class ProductionEvent {
  @Id
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.BINARY)
  @Column(length = 16)
  private java.util.UUID eventId;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "batch_id")
  private ProductionBatch batch;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "device_id")
  private Device device;

  @Column(nullable = false)
  private java.time.Instant detectedAt;

  @Column(nullable = false)
  private java.time.Instant receivedAt;

  @Column(nullable = false)
  private long countAfter;

  public java.util.UUID getEventId() {
    return eventId;
  }

  public void setEventId(java.util.UUID value) {
    this.eventId = value;
  }

  public ProductionBatch getBatch() {
    return batch;
  }

  public void setBatch(ProductionBatch value) {
    this.batch = value;
  }

  public Device getDevice() {
    return device;
  }

  public void setDevice(Device value) {
    this.device = value;
  }

  public java.time.Instant getDetectedAt() {
    return detectedAt;
  }

  public void setDetectedAt(java.time.Instant value) {
    this.detectedAt = value;
  }

  public java.time.Instant getReceivedAt() {
    return receivedAt;
  }

  public void setReceivedAt(java.time.Instant value) {
    this.receivedAt = value;
  }

  public long getCountAfter() {
    return countAfter;
  }

  public void setCountAfter(long value) {
    this.countAfter = value;
  }
}
