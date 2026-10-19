package com.empresa.sistema.controller;

import com.empresa.sistema.dto.*;
import com.empresa.sistema.security.CurrentUser;
import com.empresa.sistema.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService auth;
  private final CurrentUser current;

  public AuthController(AuthService auth, CurrentUser current) {
    this.auth = auth;
    this.current = current;
  }

  @io.swagger.v3.oas.annotations.security.SecurityRequirements
  @PostMapping("/login")
  public Responses.Token login(@Valid @RequestBody Requests.Login r) {
    return auth.login(r);
  }

  @PostMapping("/analytics/unlock")
  public Responses.Token unlock(@Valid @RequestBody Requests.AnalyticsUnlock r) {
    return auth.unlock(r);
  }

  @GetMapping("/me")
  public Responses.UserView me() {
    return Responses.UserView.of(current.get());
  }
}
