package com.empresa.sistema.service;

import com.empresa.sistema.dto.*;
import com.empresa.sistema.entity.*;
import com.empresa.sistema.exception.ApiException;
import com.empresa.sistema.repository.*;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CatalogService {
  private final UserRepository users;
  private final ScrewTypeRepository screws;
  private final PasswordEncoder encoder;

  public CatalogService(UserRepository users, ScrewTypeRepository screws, PasswordEncoder encoder) {
    this.users = users;
    this.screws = screws;
    this.encoder = encoder;
  }

  public Page<Responses.UserView> users(Pageable p) {
    return users.findAll(p).map(Responses.UserView::of);
  }

  public Responses.UserView user(Long id) {
    return Responses.UserView.of(getUser(id));
  }

  public User getUser(Long id) {
    return users.findById(id).orElseThrow(() -> ApiException.missing("Usuário"));
  }

  private String hash(String v) {
    if (v.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException("Senha deve ter no máximo 72 bytes UTF-8");
    return encoder.encode(v);
  }

  public Responses.UserView createUser(Requests.UserCreate r) {
    User u = new User();
    u.setName(r.name());
    u.setEmail(r.email().trim().toLowerCase(java.util.Locale.ROOT));
    u.setPasswordHash(hash(r.password()));
    u.setRole(r.role());
    if (r.analyticsPassword() != null) u.setAnalyticsPasswordHash(hash(r.analyticsPassword()));
    return Responses.UserView.of(users.save(u));
  }

  public Responses.UserView updateUser(Long id, Requests.UserUpdate r) {
    User u = getUser(id);
    u.setName(r.name());
    u.setEmail(r.email().trim().toLowerCase(java.util.Locale.ROOT));
    u.setRole(r.role());
    u.setActive(r.active());
    if (r.password() != null) u.setPasswordHash(hash(r.password()));
    if (r.analyticsPassword() != null) u.setAnalyticsPasswordHash(hash(r.analyticsPassword()));
    return Responses.UserView.of(u);
  }

  public void deleteUser(Long id) {
    getUser(id).setActive(false);
  }

  public Page<Responses.ScrewView> screws(Pageable p) {
    return screws.findAll(p).map(Responses.ScrewView::of);
  }

  public Responses.ScrewView screw(Long id) {
    return Responses.ScrewView.of(getScrew(id));
  }

  public ScrewType getScrew(Long id) {
    return screws.findById(id).orElseThrow(() -> ApiException.missing("Tipo de parafuso"));
  }

  public Responses.ScrewView saveScrew(Long id, Requests.Screw r) {
    ScrewType s = id == null ? new ScrewType() : getScrew(id);
    s.setName(r.name());
    s.setDescription(r.description());
    s.setPhotoUrl(r.photoUrl());
    s.setSpecifications(r.specifications());
    return Responses.ScrewView.of(screws.save(s));
  }

  public void deleteScrew(Long id) {
    getScrew(id).setActive(false);
  }
}
