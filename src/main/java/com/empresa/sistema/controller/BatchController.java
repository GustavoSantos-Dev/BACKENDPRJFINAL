package com.empresa.sistema.controller;

import com.empresa.sistema.dto.*;
import com.empresa.sistema.service.BatchService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/batches")
public class BatchController {
  private final BatchService service;

  public BatchController(BatchService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Responses.BatchView create(@Valid @RequestBody Requests.BatchCreate r) {
    return service.create(r);
  }

  @GetMapping
  public Page<Responses.BatchView> list(@PageableDefault(size = 20) Pageable p) {
    return service.list(p);
  }

  @GetMapping("/{id}")
  public Responses.BatchView get(@PathVariable Long id) {
    return service.get(id);
  }

  @GetMapping("/{id}/events")
  public Page<Responses.EventView> events(
      @PathVariable Long id, @PageableDefault(size = 50) Pageable p) {
    return service.events(id, p);
  }

  @GetMapping("/{id}/pauses")
  public List<Responses.PauseView> pauses(@PathVariable Long id) {
    return service.pauses(id);
  }

  @PostMapping("/{id}/start")
  @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
  public Responses.BatchView start(@PathVariable Long id) {
    return service.transition(id, "start");
  }

  @PostMapping("/{id}/pause")
  @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
  public Responses.BatchView pause(@PathVariable Long id) {
    return service.transition(id, "pause");
  }

  @PostMapping("/{id}/resume")
  @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
  public Responses.BatchView resume(@PathVariable Long id) {
    return service.transition(id, "resume");
  }

  @PostMapping("/{id}/finish")
  @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
  public Responses.BatchView finish(@PathVariable Long id) {
    return service.transition(id, "finish");
  }
}
