package com.empresa.sistema.controller;

import com.empresa.sistema.dto.*;
import com.empresa.sistema.service.ProductionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/production")
@SecurityRequirements({@SecurityRequirement(name = "deviceKey")})
public class ProductionController {
  private final ProductionService service;

  public ProductionController(ProductionService service) {
    this.service = service;
  }

  @PostMapping("/events")
  public Responses.EventAccepted event(
      @RequestHeader(value = "X-Device-Key", required = false) String key,
      @Valid @RequestBody Requests.Event r) {
    return service.accept(r, key);
  }

  @GetMapping("/device-state")
  public List<Responses.BatchView> state(
      @RequestHeader("X-Device-Id") String id,
      @RequestHeader(value = "X-Device-Key", required = false) String key) {
    return service.state(id, key);
  }
}
