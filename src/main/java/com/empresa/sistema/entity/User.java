package com.empresa.sistema.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, unique = true, length = 254)
  private String email;

  @Column(nullable = false, length = 100)
  private String passwordHash;

  @Column(length = 100)
  private String analyticsPasswordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role;

  @Column(nullable = false)
  private boolean active = true;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    this.id = value;
  }

  public String getName() {
    return name;
  }

  public void setName(String value) {
    this.name = value;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String value) {
    this.email = value;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String value) {
    this.passwordHash = value;
  }

  public String getAnalyticsPasswordHash() {
    return analyticsPasswordHash;
  }

  public void setAnalyticsPasswordHash(String value) {
    this.analyticsPasswordHash = value;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role value) {
    this.role = value;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean value) {
    this.active = value;
  }
}
