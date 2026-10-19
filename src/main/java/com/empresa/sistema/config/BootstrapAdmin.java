package com.empresa.sistema.config;

import com.empresa.sistema.entity.*;
import com.empresa.sistema.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdmin implements CommandLineRunner {
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final String email, password;

  public BootstrapAdmin(
      UserRepository users,
      PasswordEncoder encoder,
      @Value("${app.bootstrap-email}") String email,
      @Value("${app.bootstrap-password}") String password) {
    this.users = users;
    this.encoder = encoder;
    this.email = email;
    this.password = password;
  }

  public void run(String... args) {
    if (users.count() != 0 || email.isBlank()) return;
    if (password.length() < 12
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
        || !email.contains("@"))
      throw new IllegalArgumentException(
          "Configure credenciais válidas para o administrador inicial");
    User u = new User();
    u.setName("Administrador");
    u.setEmail(email.trim().toLowerCase(java.util.Locale.ROOT));
    u.setPasswordHash(encoder.encode(password));
    u.setRole(Role.ADMIN);
    users.save(u);
  }
}
