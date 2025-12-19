package com.planbana.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class JwtService {

  private final Key key;
  private final long accessMinutes;
  private final long refreshDays;

  public JwtService(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.access-token-minutes}") long accessMinutes,
      @Value("${app.jwt.refresh-token-days}") long refreshDays) {
    this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    this.accessMinutes = accessMinutes;
    this.refreshDays = refreshDays;
  }

  public String generateAccess(String subject, Set<String> roles, int tokenVersion) {
    return generateAccess(subject, Map.of(
        "roles", List.copyOf(roles),
        "tv", tokenVersion));
  }

  public String generateAccess(String subject, Map<String, Object> claims) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(accessMinutes * 60);
    return Jwts.builder()
        .setSubject(subject)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(exp))
        .addClaims(claims)
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }

  public String generateRefresh(String subject) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(refreshDays * 24 * 60 * 60);
    return Jwts.builder()
        .setSubject(subject)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(exp))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }

  public boolean validateToken(String token) {
    try {
      Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public String getUsername(String token) {
    Claims claims = Jwts.parserBuilder()
        .setSigningKey(key)
        .build()
        .parseClaimsJws(token)
        .getBody();
    return claims.getSubject();
  }

  @SuppressWarnings("unchecked")
  public List<String> getRoles(String token) {
    Claims claims = Jwts.parserBuilder()
        .setSigningKey(key)
        .build()
        .parseClaimsJws(token)
        .getBody();
    Object roles = claims.get("roles");
    if (roles instanceof List<?> list) {
      return list.stream().map(Object::toString).toList();
    }
    return List.of();
  }

  public Integer getTokenVersion(String token) {
    Claims claims = Jwts.parserBuilder()
        .setSigningKey(key)
        .build()
        .parseClaimsJws(token)
        .getBody();

    Object tv = claims.get("tv");
    if (tv == null) {
      return 0; // backward compatibility
    }

    if (tv instanceof Number n) {
      return n.intValue();
    }

    return Integer.parseInt(tv.toString());
  }

}
