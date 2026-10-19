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
@RequestMapping("/api/screws")
public class ScrewController {
  private final CatalogService catalog;

  public ScrewController(CatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  public Page<Responses.ScrewView> list(@PageableDefault(size = 20) Pageable p) {
    return catalog.screws(p);
  }

  @GetMapping("/{id}")
  public Responses.ScrewView get(@PathVariable Long id) {
    return catalog.screw(id);
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public Responses.ScrewView create(@Valid @RequestBody Requests.Screw r) {
    return catalog.saveScrew(null, r);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Responses.ScrewView update(@PathVariable Long id, @Valid @RequestBody Requests.Screw r) {
    return catalog.saveScrew(id, r);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    catalog.deleteScrew(id);
  }
}
