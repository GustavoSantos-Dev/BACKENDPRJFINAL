package com.empresa.sistema.security;

import com.empresa.sistema.dto.Responses.Token;
import com.empresa.sistema.entity.User;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
  private final JwtEncoder encoder;
  private final String issuer;

  public TokenService(JwtEncoder encoder, @Value("${app.issuer}") String issuer) {
    this.encoder = encoder;
    this.issuer = issuer;
  }

  public Token issue(User u, boolean analytics) {
    long duration = analytics ? 600 : 3600;
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(u.getId().toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(duration))
            .claim("role", u.getRole().name())
            .claim("analytics", analytics)
            .build();
    String token =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return new Token(token, "Bearer", duration);
  }
}
