package com.empresa.sistema.controller;

import com.empresa.sistema.dto.*;
import com.empresa.sistema.service.DeviceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices")
@PreAuthorize("hasRole('ADMIN')")
public class DeviceController {
  private final DeviceService service;

  public DeviceController(DeviceService service) {
    this.service = service;
  }

  @GetMapping
  public List<Responses.DeviceView> list() {
    return service.list();
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Responses.DeviceKey create(@Valid @RequestBody Requests.DeviceCreate r) {
    return service.create(r);
  }

  @PostMapping("/{id}/rotate-key")
  public Responses.DeviceKey rotate(@PathVariable String id) {
    return service.rotate(id);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id) {
    service.deactivate(id);
  }
}
