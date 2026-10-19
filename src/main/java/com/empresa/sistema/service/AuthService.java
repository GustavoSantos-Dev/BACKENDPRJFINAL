package com.empresa.sistema.service;

import com.empresa.sistema.dto.*;
import com.empresa.sistema.entity.*;
import com.empresa.sistema.exception.ApiException;
import com.empresa.sistema.repository.UserRepository;
import com.empresa.sistema.security.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final TokenService tokens;
  private final CurrentUser current;
  private final String dummyHash;

  public AuthService(
      UserRepository users, PasswordEncoder encoder, TokenService tokens, CurrentUser current) {
    this.users = users;
    this.encoder = encoder;
    this.tokens = tokens;
    this.current = current;
    dummyHash = encoder.encode(java.util.UUID.randomUUID().toString());
  }

  public Responses.Token login(Requests.Login r) {
    var u = users.findByEmail(r.email().trim().toLowerCase(java.util.Locale.ROOT));
    boolean valid = encoder.matches(r.password(), u.map(User::getPasswordHash).orElse(dummyHash));
    if (!valid || u.isEmpty() || !u.get().isActive())
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Credenciais inválidas");
    return tokens.issue(u.get(), false);
  }

  public Responses.Token unlock(Requests.AnalyticsUnlock r) {
    User u = current.get();
    if (u.getRole() == Role.OPERADOR)
      throw ApiException.forbidden("Perfil sem acesso ao Analytics");
    String hash =
        u.getAnalyticsPasswordHash() == null ? u.getPasswordHash() : u.getAnalyticsPasswordHash();
    if (!encoder.matches(r.password(), hash))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Senha inválida");
    return tokens.issue(u, true);
  }
}
