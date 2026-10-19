package com.empresa.sistema.security;

import com.empresa.sistema.entity.User;
import com.empresa.sistema.exception.ApiException;
import com.empresa.sistema.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
  private final UserRepository users;

  public CurrentUser(UserRepository users) {
    this.users = users;
  }

  public User get() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt))
      throw ApiException.forbidden("Autenticação necessária");
    User u =
        users
            .findById(Long.valueOf(jwt.getSubject()))
            .orElseThrow(() -> ApiException.forbidden("Usuário indisponível"));
    if (!u.isActive()) throw ApiException.forbidden("Usuário inativo");
    return u;
  }
}
