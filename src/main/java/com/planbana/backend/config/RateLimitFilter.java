package com.planbana.backend.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter implements Filter {

  private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

  @Value("${app.rateLimit.requestsPerMinute:100}")
  private int requestsPerMinute;

  private Bucket resolveBucket(String key) {
    return cache.computeIfAbsent(key, k -> {
      Refill refill = Refill.greedy(requestsPerMinute, Duration.ofMinutes(1));
      Bandwidth limit = Bandwidth.classic(requestsPerMinute, refill);
      return Bucket4j.builder().addLimit(limit).build();
    });
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse res = (HttpServletResponse) response;

    String key = req.getRemoteAddr();
    Bucket bucket = resolveBucket(key);

    if (bucket.tryConsume(1)) {
      chain.doFilter(request, response);
    } else {
      // Add CORS headers so the browser can read the error (otherwise it sees "Failed to fetch")
      String origin = req.getHeader("Origin");
      if (origin != null && (origin.equals("http://localhost:3000") || origin.equals("http://127.0.0.1:3000"))) {
        res.setHeader("Access-Control-Allow-Origin", origin);
        res.setHeader("Access-Control-Allow-Credentials", "true");
      }
      res.setStatus(429);
      res.setContentType("application/json");
      res.getWriter().write("{\"error\":\"RATE_LIMITED\",\"message\":\"Too Many Requests. Please try again later.\"}");
    }
  }
}
