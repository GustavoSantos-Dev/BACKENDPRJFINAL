package com.empresa.sistema.controller;

import com.empresa.sistema.dto.*;
import com.empresa.sistema.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {
  private final CatalogService catalog;

  public UserController(CatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  public Page<Responses.UserView> list(@PageableDefault(size = 20) Pageable p) {
    return catalog.users(p);
  }

  @GetMapping("/{id}")
  public Responses.UserView get(@PathVariable Long id) {
    return catalog.user(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Responses.UserView create(@Valid @RequestBody Requests.UserCreate r) {
    return catalog.createUser(r);
  }

  @PutMapping("/{id}")
  public Responses.UserView update(
      @PathVariable Long id, @Valid @RequestBody Requests.UserUpdate r) {
    return catalog.updateUser(id, r);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    catalog.deleteUser(id);
  }
}
