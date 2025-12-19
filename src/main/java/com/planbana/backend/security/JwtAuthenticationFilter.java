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

import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final UserRepository userRepo;

  public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepo) {
    this.jwtService = jwtService;
    this.userRepo = userRepo;
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
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    String token = resolveToken(request);

    // 🔹 If no token → continue (public or unauthenticated request)
    if (token == null) {
      filterChain.doFilter(request, response);
      return;
    }

    // 🔹 If token is invalid → continue (will be rejected later by security)
    if (!jwtService.validateToken(token)) {
      filterChain.doFilter(request, response);
      return;
    }

    // 🔹 Extract data from token
    String phone = jwtService.getUsername(token);
    Integer tokenTv = jwtService.getTokenVersion(token);

    User user = userRepo.findByPhone(phone).orElse(null);

    // 🔥 HARD BLOCK CHECK (logout from all devices)
    if (user == null ||
        Boolean.TRUE.equals(user.getDisabled()) ||
        !user.getTokenVersion().equals(tokenTv)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("application/json");
      response.getWriter().write("""
            {
              "error": "ACCOUNT_BLOCKED",
              "message": "Your account has been blocked. Please contact the administrator."
            }
          """);
      return; // ⛔ STOP FILTER CHAIN
    }

    // 🔹 User is valid → authenticate
    List<SimpleGrantedAuthority> authorities = jwtService.getRoles(token)
        .stream()
        .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
        .map(SimpleGrantedAuthority::new)
        .toList();

    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(phone, null, authorities);

    SecurityContextHolder.getContext().setAuthentication(authToken);

    // 🔹 Continue request
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
