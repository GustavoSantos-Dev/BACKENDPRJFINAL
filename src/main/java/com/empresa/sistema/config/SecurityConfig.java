package com.empresa.sistema.config;

import com.empresa.sistema.repository.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  private SecretKeySpec key(String secret) {
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32)
      throw new IllegalArgumentException("JWT_SECRET precisa conter pelo menos 32 bytes");
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(@Value("${app.jwt-secret}") String secret) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key(secret)));
  }

  @Bean
  JwtDecoder jwtDecoder(
      @Value("${app.jwt-secret}") String secret, @Value("${app.issuer}") String issuer) {
    var decoder =
        NimbusJwtDecoder.withSecretKey(key(secret)).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    return decoder;
  }

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http, UserRepository users) throws Exception {
    http.csrf(c -> c.disable())
        .cors(c -> {})
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/auth/login",
                        "/api/production/events",
                        "/api/production/device-state",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/api/analytics/**")
                    .hasAuthority("ANALYTICS")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((r, s, x) -> failure(r, s, 401, "UNAUTHORIZED"))
                    .accessDeniedHandler((r, s, x) -> failure(r, s, 403, "FORBIDDEN")))
        .oauth2ResourceServer(
            o ->
                o.authenticationEntryPoint((r, s, x) -> failure(r, s, 401, "UNAUTHORIZED"))
                    .accessDeniedHandler((r, s, x) -> failure(r, s, 403, "FORBIDDEN"))
                    .jwt(
                        j ->
                            j.jwtAuthenticationConverter(
                                jwt -> {
                                  com.empresa.sistema.entity.User u;
                                  try {
                                    u =
                                        users
                                            .findById(Long.valueOf(jwt.getSubject()))
                                            .orElseThrow(
                                                () ->
                                                    new BadCredentialsException(
                                                        "Usuário indisponível"));
                                  } catch (NumberFormatException e) {
                                    throw new BadCredentialsException("Token inválido");
                                  }
                                  if (!u.isActive())
                                    throw new BadCredentialsException("Usuário inativo");
                                  List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                                  authorities.add(
                                      new SimpleGrantedAuthority("ROLE_" + u.getRole().name()));
                                  if (Boolean.TRUE.equals(jwt.getClaim("analytics"))
                                      && u.getRole() != com.empresa.sistema.entity.Role.OPERADOR)
                                    authorities.add(new SimpleGrantedAuthority("ANALYTICS"));
                                  return new JwtAuthenticationToken(jwt, authorities);
                                })));
    return http.build();
  }

  private static void failure(
      HttpServletRequest request, HttpServletResponse response, int status, String code)
      throws java.io.IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    var body =
        java.util.Map.of(
            "timestamp",
            java.time.Instant.now().toString(),
            "status",
            status,
            "code",
            code,
            "message",
            "Acesso não autorizado",
            "path",
            request.getRequestURI(),
            "details",
            java.util.Map.of());
    response
        .getWriter()
        .write(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(body));
  }

  @Bean
  CorsConfigurationSource cors(@Value("${app.cors-origins}") String origins) {
    var c = new CorsConfiguration();
    c.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Device-Key", "X-Device-Id"));
    c.setExposedHeaders(List.of("Content-Disposition"));
    c.setAllowCredentials(false);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", c);
    return source;
  }
}
