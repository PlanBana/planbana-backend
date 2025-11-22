package com.planbana.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  public JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();

    // Skip only the real auth endpoints
    return path.startsWith("/api/auth/");
  }

  // @Override
  // protected void doFilterInternal(HttpServletRequest request,
  // HttpServletResponse response,
  // FilterChain filterChain) throws ServletException, IOException {

  // String token = resolveToken(request);

  // if (token != null && jwtService.validateToken(token)) {
  // String username = jwtService.getUsername(token);
  // List<String> roles = jwtService.getRoles(token);

  // if (username != null &&
  // SecurityContextHolder.getContext().getAuthentication() == null) {
  // // Ensure roles are Spring-friendly ("ROLE_USER" instead of "USER")
  // List<SimpleGrantedAuthority> authorities = roles.stream()
  // .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
  // .map(SimpleGrantedAuthority::new)
  // .toList();

  // // List<SimpleGrantedAuthority> authorities = roles.stream()
  // // .map(SimpleGrantedAuthority::new)
  // // .toList();

  // UsernamePasswordAuthenticationToken authToken = new
  // UsernamePasswordAuthenticationToken(
  // username, // principal
  // null,
  // authorities);

  // SecurityContextHolder.getContext().setAuthentication(authToken);

  // System.out.println("✅ Authenticated " + username + " with roles " +
  // authorities);
  // }
  // }

  // filterChain.doFilter(request, response);
  // }

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    String uri = request.getRequestURI();
    System.out.println("---- JWT FILTER ----");
    System.out.println("Request: " + request.getMethod() + " " + uri);

    String token = resolveToken(request);

    if (token == null) {
      System.out.println("No token found");
    } else {
      System.out.println("Token found: " + token.substring(0, 10) + "...");
      System.out.println("Token roles (raw): " + jwtService.getRoles(token));
    }

    if (token != null && jwtService.validateToken(token)) {
      String username = jwtService.getUsername(token);
      List<String> roles = jwtService.getRoles(token);

      System.out.println("Valid token for: " + username);
      System.out.println("Roles extracted: " + roles);

      List<SimpleGrantedAuthority> authorities = roles.stream()
          .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
          .map(SimpleGrantedAuthority::new)
          .toList();

      System.out.println("Authorities applied: " + authorities);

      UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(username, null,
          authorities);

      SecurityContextHolder.getContext().setAuthentication(authToken);
    } else {
      System.out.println("Token INVALID");
    }

    filterChain.doFilter(request, response);
  }

  private String resolveToken(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
      return header.substring(7);
    }
    if (request.getCookies() != null) {
      return Arrays.stream(request.getCookies())
          .filter(c -> "access_token".equals(c.getName()))
          .map(Cookie::getValue)
          .findFirst()
          .orElse(null);
    }
    return null;
  }
}
