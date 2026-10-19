package com.empresa.sistema.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "production_batches")
public class ProductionBatch {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 64)
  private String operationId;

  @Column(nullable = false)
  private long target;

  @Column(nullable = false)
  private long currentCount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private BatchStatus status = BatchStatus.CONFIGURADO;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "screw_type_id")
  private ScrewType screwType;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "operator_id")
  private User operator;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "device_id")
  private Device device;

  @Column(nullable = false)
  private java.time.Instant createdAt = java.time.Instant.now();

  private java.time.Instant startedAt;
  private java.time.Instant finishedAt;
  @Version private long version;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public String getOperationId() {
    return operationId;
  }

  public void setOperationId(String value) {
    this.operationId = value;
  }

  public long getTarget() {
    return target;
  }

  public void setTarget(long value) {
    this.target = value;
  }

  public long getCurrentCount() {
    return currentCount;
  }

  public void setCurrentCount(long value) {
    this.currentCount = value;
  }

  public BatchStatus getStatus() {
    return status;
  }

  public void setStatus(BatchStatus value) {
    this.status = value;
  }

  public ScrewType getScrewType() {
    return screwType;
  }

  public void setScrewType(ScrewType value) {
    this.screwType = value;
  }

  public User getOperator() {
    return operator;
  }

  public void setOperator(User value) {
    this.operator = value;
  }

  public Device getDevice() {
    return device;
  }

  public void setDevice(Device value) {
    this.device = value;
  }

  public java.time.Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(java.time.Instant value) {
    this.createdAt = value;
  }

  public java.time.Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(java.time.Instant value) {
    this.startedAt = value;
  }

  public java.time.Instant getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(java.time.Instant value) {
    this.finishedAt = value;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long value) {
    this.version = value;
  }
}
