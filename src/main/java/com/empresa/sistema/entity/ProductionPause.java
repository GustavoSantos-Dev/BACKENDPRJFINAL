package com.empresa.sistema.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "production_pauses")
public class ProductionPause {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "batch_id")
  private ProductionBatch batch;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "paused_by")
  private User pausedBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "resumed_by")
  private User resumedBy;

  @Column(nullable = false)
  private java.time.Instant pausedAt;

  private java.time.Instant resumedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public ProductionBatch getBatch() {
    return batch;
  }

  public void setBatch(ProductionBatch value) {
    this.batch = value;
  }

  public User getPausedBy() {
    return pausedBy;
  }

  public void setPausedBy(User value) {
    this.pausedBy = value;
  }

  public User getResumedBy() {
    return resumedBy;
  }

  public void setResumedBy(User value) {
    this.resumedBy = value;
  }

  public java.time.Instant getPausedAt() {
    return pausedAt;
  }

  public void setPausedAt(java.time.Instant value) {
    this.pausedAt = value;
  }

  public java.time.Instant getResumedAt() {
    return resumedAt;
  }

  public void setResumedAt(java.time.Instant value) {
    this.resumedAt = value;
  }
}
