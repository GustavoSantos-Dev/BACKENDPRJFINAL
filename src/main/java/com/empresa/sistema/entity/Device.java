package com.empresa.sistema.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "devices")
public class Device {
  @Id
  @Column(length = 64)
  private String id;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 64)
  private String apiKeyHash;

  @Column(nullable = false)
  private boolean active = true;

  public String getId() {
    return id;
  }

  public void setId(String value) {
    this.id = value;
  }

  public String getName() {
    return name;
  }

  public void setName(String value) {
    this.name = value;
  }

  public String getApiKeyHash() {
    return apiKeyHash;
  }

  public void setApiKeyHash(String value) {
    this.apiKeyHash = value;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean value) {
    this.active = value;
  }
}
