package com.planbana.backend.events.share;

import com.planbana.backend.events.Event;
import com.planbana.backend.events.EventRepository;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping
public class ShareLinkController {

  private final ShareLinkRepository links;
  private final EventRepository events;
  private final UserRepository users;

  /** Base URL used to build web links (set in application.yml: app.publicBaseUrl: https://your-domain) */
  private final String publicBaseUrl;

  public ShareLinkController(
      ShareLinkRepository links,
      EventRepository events,
      UserRepository users,
      @Value("${app.publicBaseUrl:https://planbana.com}") String publicBaseUrl
  ) {
    this.links = links;
    this.events = events;
    this.users = users;
    this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
  }

  private static String trimTrailingSlash(String s) {
    return (s != null && s.endsWith("/")) ? s.substring(0, s.length() - 1) : s;
  }

  // ================
  // Create share link
  // ================

  public static class CreateShareLinkRequest {
    public String type;                 // "PUBLIC" | "INVITE"
    public String scope;                // "VIEW" | "JOIN" | "VIEW_JOIN"
    public Instant expiresAt;           // optional
    public Integer maxUses;             // optional
    public String channel;              // optional (analytics)
    public String campaign;             // optional (analytics)
  }

  public static class ShareLinkResponse {
    public String id;
    public String url;         // full https URL (null for INVITE in listing)
    public String type;
    public String scope;
    public Instant expiresAt;
    public Integer maxUses;
    public Integer uses;
    public boolean disabled;

    /** Only present on create for INVITE */
    public String code;
  }

  @PostMapping("/api/events/{eventId}/share-links")
  public ShareLinkResponse create(
      @PathVariable String eventId,
      @RequestBody CreateShareLinkRequest req,
      Authentication auth
  ) {
    User caller = getCaller(auth);
    Event e = events.findById(eventId).orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    requireOwner(caller, e);

    ShareLink.Type type = parseType(req.type);
    ShareLink.Scope scope = parseScope(req.scope);

    ShareLink sl = new ShareLink();
    sl.setEventId(eventId);
    sl.setCreatedByUserId(caller.getId());
    sl.setType(type);
    sl.setScope(scope);
    sl.setExpiresAt(req.expiresAt);
    sl.setMaxUses(req.maxUses);
    sl.setChannel(req.channel);
    sl.setCampaign(req.campaign);

    String code = null;
    if (type == ShareLink.Type.INVITE) {
      code = generateCode();
      sl.setCodeHash(sha256Hex(code));
    }

    links.save(sl);

    String url = (type == ShareLink.Type.INVITE)
        ? publicBaseUrl + "/i/" + code
        : publicBaseUrl + "/e/" + eventId;

    ShareLinkResponse out = new ShareLinkResponse();
    out.id = sl.getId();
    out.url = url;
    out.type = type.name();
    out.scope = scope.name();
    out.expiresAt = sl.getExpiresAt();
    out.maxUses = sl.getMaxUses();
    out.uses = sl.getUses();
    out.disabled = sl.isDisabled();
    out.code = code; // only on create for INVITE

    return out;
  }

  // =================
  // List (owner-only)
  // =================

  @GetMapping("/api/events/{eventId}/share-links")
  public List<ShareLinkResponse> list(@PathVariable String eventId, Authentication auth) {
    User caller = getCaller(auth);
    Event e = events.findById(eventId).orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    requireOwner(caller, e);

    List<ShareLink> all = links.findByEventIdAndCreatedByUserId(eventId, caller.getId());
    List<ShareLinkResponse> out = new ArrayList<>(all.size());
    for (ShareLink sl : all) {
      ShareLinkResponse r = new ShareLinkResponse();
      r.id = sl.getId();
      r.type = sl.getType().name();
      r.scope = sl.getScope().name();
      r.expiresAt = sl.getExpiresAt();
      r.maxUses = sl.getMaxUses();
      r.uses = sl.getUses();
      r.disabled = sl.isDisabled();
      // For INVITE links we do not reveal the code again, so URL can't be reconstructed
      r.url = (sl.getType() == ShareLink.Type.PUBLIC) ? publicBaseUrl + "/e/" + sl.getEventId() : null;
      out.add(r);
    }
    return out;
  }

  // =========
  // Revoke
  // =========

  @DeleteMapping("/api/share-links/{shareLinkId}")
  public Map<String, String> revoke(@PathVariable String shareLinkId, Authentication auth) {
    User caller = getCaller(auth);
    ShareLink sl = links.findById(shareLinkId).orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Share link not found"));
    Event e = events.findById(sl.getEventId()).orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    requireOwner(caller, e);

    sl.setDisabled(true);
    links.save(sl);
    return Map.of("message", "revoked");
  }

  // ===================
  // Resolve (JSON API)
  // ===================

  public static class ResolveResponse {
    public boolean valid;
    public String eventId;
    public String scope;
    public Instant expiresAt;
    public Integer maxUses;

    public ResolveResponse(boolean valid, String eventId, String scope, Instant expiresAt, Integer maxUses) {
      this.valid = valid;
      this.eventId = eventId;
      this.scope = scope;
      this.expiresAt = expiresAt;
      this.maxUses = maxUses;
    }
  }

  @GetMapping("/api/share-links/{code}/resolve")
  public ResolveResponse resolve(@PathVariable String code) {
    String h = sha256Hex(code);
    ShareLink sl = links.findByCodeHash(h).orElse(null);
    if (sl == null) {
      return new ResolveResponse(false, null, null, null, null);
    }

    boolean ok = sl.isValidAt(Instant.now());
    if (ok) {
      sl.setUses(Optional.ofNullable(sl.getUses()).orElse(0) + 1);
      links.save(sl);
    }
    return new ResolveResponse(
        ok,
        sl.getEventId(),
        sl.getScope().name(),
        sl.getExpiresAt(),
        sl.getMaxUses()
    );
  }

  // ==========================
  // Redirect helper (/r/{code})
  // ==========================

  @GetMapping("/r/{code}")
  public ResponseEntity<Void> redirect(@PathVariable String code) {
    String h = sha256Hex(code);
    links.findByCodeHash(h).ifPresent(sl -> {
      if (sl.isValidAt(Instant.now())) {
        sl.setUses(Optional.ofNullable(sl.getUses()).orElse(0) + 1);
        links.save(sl);
      }
    });
    URI target = URI.create(publicBaseUrl + "/i/" + code);
    HttpHeaders headers = new HttpHeaders();
    headers.setLocation(target);
    return ResponseEntity.status(302).headers(headers).build();
  }

  // =========
  // helpers
  // =========

  private User getCaller(Authentication auth) {
    return users.findByPhone(auth.getName())
        .or(() -> users.findByEmail(auth.getName()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
  }

  private void requireOwner(User caller, Event e) {
    if (e == null || caller == null || !Objects.equals(e.getCreatedByUserId(), caller.getId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not event owner");
    }
  }

  private static ShareLink.Type parseType(String s) {
    if (s == null || s.isBlank()) return ShareLink.Type.INVITE;
    return ShareLink.Type.valueOf(s.trim().toUpperCase(Locale.ROOT));
  }

  private static ShareLink.Scope parseScope(String s) {
    if (s == null || s.isBlank()) return ShareLink.Scope.VIEW;
    return ShareLink.Scope.valueOf(s.trim().toUpperCase(Locale.ROOT));
  }

  private static final SecureRandom RNG = new SecureRandom();

  private static String generateCode() {
    byte[] buf = new byte[24]; // 192 bits
    RNG.nextBytes(buf);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
  }

  private static String sha256Hex(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(d.length * 2);
      for (byte b : d) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
