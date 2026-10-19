package com.empresa.sistema.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "screw_types")
public class ScrewType {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 1000)
  private String description;

  @Column(length = 1000)
  private String photoUrl;

  @Column(length = 2000)
  private String specifications;

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

  public String getDescription() {
    return description;
  }

  public void setDescription(String value) {
    this.description = value;
  }

  public String getPhotoUrl() {
    return photoUrl;
  }

  public void setPhotoUrl(String value) {
    this.photoUrl = value;
  }

  public String getSpecifications() {
    return specifications;
  }

  public void setSpecifications(String value) {
    this.specifications = value;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean value) {
    this.active = value;
  }
}
