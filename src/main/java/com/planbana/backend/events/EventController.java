package com.planbana.backend.events;

import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/events")
public class EventController {
  private final EventRepository repo;
  private final MongoTemplate mongo;
  private final UserRepository users;

  public EventController(EventRepository repo, MongoTemplate mongo, UserRepository users) {
    this.repo = repo;
    this.mongo = mongo;
    this.users = users;
  }

  public static class CreateEvent {
    public String title;
    public String description;
    public Instant startAt;
    public Instant endAt;
    public String imageUrl;
    public Double lat;
    public Double lng;
    public Double startLat;
    public Double startLng;
    public Double destinationLat;
    public Double destinationLng;
    public String category;
    public String pricingType;
    public Double price;
    public Integer maxParticipants;
    public Set<String> tags;
  }

  public static class JoinStatusResponse {
    public String status;
    public JoinStatusResponse(String status) { this.status = status; }
  }

  @PostMapping
  public Event create(@RequestBody CreateEvent req, Authentication auth) {
    User u = getCurrentUser(auth);
    Event e = new Event();
    e.setTitle(req.title);
    e.setDescription(req.description);
    e.setStartAt(req.startAt);
    e.setEndAt(req.endAt);
    e.setImageUrl(req.imageUrl);

    if (req.lng != null && req.lat != null) {
      e.setLocation(new GeoJsonPoint(req.lng, req.lat));
    }

    if (req.startLng != null && req.startLat != null) {
      e.setStartLocation(new GeoJsonPoint(req.startLng, req.startLat));
    }
    if (req.destinationLng != null && req.destinationLat != null) {
      e.setDestinationLocation(new GeoJsonPoint(req.destinationLng, req.destinationLat));
    }

    e.setCategory(req.category);
    e.setPricingType(req.pricingType);
    e.setPrice(req.price);
    e.setMaxParticipants(req.maxParticipants);
    e.setTags(req.tags != null ? req.tags : Set.of());
    e.setCreatedByUserId(u.getId());

    return repo.save(e);
  }

  @GetMapping
  public List<Event> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Double lat,
      @RequestParam(required = false) Double lng,
      @RequestParam(required = false, defaultValue = "25") Double radiusKm,
      @RequestParam(required = false) Instant startDate,
      @RequestParam(required = false) Instant endDate,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String category
  ) {
    Query query = new Query();

    if (q != null && !q.isBlank()) {
      query.addCriteria(new Criteria().orOperator(
          Criteria.where("title").regex(q, "i"),
          Criteria.where("description").regex(q, "i"),
          Criteria.where("tags").in(q)
      ));
    }

    if (category != null && !category.isBlank()) {
      query.addCriteria(Criteria.where("category").is(category));
    }

    if (lat != null && lng != null) {
      Criteria nearLegacy = Criteria.where("location")
          .nearSphere(new Point(lng, lat))
          .maxDistance(radiusKm / 6378.1);
      Criteria nearStart = Criteria.where("startLocation")
          .nearSphere(new Point(lng, lat))
          .maxDistance(radiusKm / 6378.1);
      query.addCriteria(new Criteria().orOperator(nearLegacy, nearStart));
    }

    if (startDate != null) {
      query.addCriteria(Criteria.where("startAt").gte(startDate));
    }
    if (endDate != null) {
      query.addCriteria(Criteria.where("endAt").lte(endDate));
    }

    query.with(PageRequest.of(page, size));
    return mongo.find(query, Event.class);
  }

  @GetMapping("/{id}")
  public Event get(@PathVariable String id) {
    return repo.findById(id).orElseThrow();
  }

  @PatchMapping("/{id}")
  public Map<String, String> update(@PathVariable String id, @RequestBody Map<String, Object> body, Authentication auth) {
    Event e = repo.findById(id).orElseThrow();
    if (body.containsKey("title")) e.setTitle((String) body.get("title"));
    if (body.containsKey("description")) e.setDescription((String) body.get("description"));
    if (body.containsKey("imageUrl")) e.setImageUrl((String) body.get("imageUrl"));
    if (body.containsKey("category")) e.setCategory((String) body.get("category"));
    if (body.containsKey("pricingType")) e.setPricingType((String) body.get("pricingType"));
    if (body.containsKey("price")) e.setPrice(body.get("price") == null ? null : Double.valueOf(body.get("price").toString()));
    if (body.containsKey("maxParticipants")) e.setMaxParticipants(body.get("maxParticipants") == null ? null : Integer.valueOf(body.get("maxParticipants").toString()));

    if (body.containsKey("startLocation")) {
      Map<?,?> p = (Map<?,?>) body.get("startLocation");
      if (p != null && p.get("lng") != null && p.get("lat") != null) {
        e.setStartLocation(new GeoJsonPoint(Double.parseDouble(p.get("lng").toString()), Double.parseDouble(p.get("lat").toString())));
      }
    }
    if (body.containsKey("destinationLocation")) {
      Map<?,?> p = (Map<?,?>) body.get("destinationLocation");
      if (p != null && p.get("lng") != null && p.get("lat") != null) {
        e.setDestinationLocation(new GeoJsonPoint(Double.parseDouble(p.get("lng").toString()), Double.parseDouble(p.get("lat").toString())));
      }
    }

    repo.save(e);
    return Map.of("message", "updated");
  }

  @DeleteMapping("/{id}")
  public Map<String, String> delete(@PathVariable String id) {
    repo.deleteById(id);
    return Map.of("message", "deleted");
  }

  @PostMapping("/{id}/join")
  public JoinStatusResponse requestJoin(@PathVariable String id, Authentication auth) {
    User u = getCurrentUser(auth);
    Event e = repo.findById(id).orElseThrow();

    if (Objects.equals(e.getCreatedByUserId(), u.getId())) {
      ensureParticipant(e, u.getId());
      repo.save(e);
      return new JoinStatusResponse(Event.JoinStatus.APPROVED.name());
    }

    if (e.getParticipants().contains(u.getId())) {
      return new JoinStatusResponse(Event.JoinStatus.APPROVED.name());
    }

    Event.JoinRequest existing = findJoinRequestForUser(e, u.getId());
    if (existing != null) {
      return new JoinStatusResponse(existing.getStatus().name());
    }

    boolean hasCapacity = e.getMaxParticipants() == null || e.getParticipants().size() < e.getMaxParticipants();

    if (hasCapacity) {
      ensureParticipant(e, u.getId());
      repo.save(e);
      return new JoinStatusResponse(Event.JoinStatus.APPROVED.name());
    } else {
      Event.JoinRequest jr = new Event.JoinRequest(u.getId(), Event.JoinStatus.WAITLISTED, Instant.now());
      e.getJoinRequests().add(jr);
      repo.save(e);
      return new JoinStatusResponse(Event.JoinStatus.WAITLISTED.name());
    }
  }

  @GetMapping("/{id}/join/status")
  public JoinStatusResponse myJoinStatus(@PathVariable String id, Authentication auth) {
    User u = getCurrentUser(auth);
    Event e = repo.findById(id).orElseThrow();

    if (e.getParticipants().contains(u.getId())) {
      return new JoinStatusResponse(Event.JoinStatus.APPROVED.name());
    }
    Event.JoinRequest jr = findJoinRequestForUser(e, u.getId());
    if (jr != null) {
      return new JoinStatusResponse(jr.getStatus().name());
    }
    return new JoinStatusResponse(Event.JoinStatus.NONE.name());
  }

  @GetMapping("/{id}/join/requests")
  public List<Event.JoinRequest> listJoinRequests(@PathVariable String id, Authentication auth) {
    Event e = repo.findById(id).orElseThrow();
    assertOwner(auth, e);
    return e.getJoinRequests();
  }

  @PatchMapping("/{id}/join/{userId}")
  public Map<String, Object> changeJoinStatus(
      @PathVariable String id,
      @PathVariable String userId,
      @RequestParam String action,
      Authentication auth
  ) {
    Event e = repo.findById(id).orElseThrow();
    assertOwner(auth, e);

    String act = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
    switch (act) {
      case "approve" -> approveUser(e, userId);
      case "reject"  -> rejectUser(e, userId);
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action must be 'approve' or 'reject'");
    }

    repo.save(e);
    Event.JoinRequest jr = findOrCreateJoinRequest(e, userId);
    return Map.of(
        "message", "updated",
        "status", jr.getStatus().name(),
        "participantsCount", e.getParticipants().size()
    );
  }

  @GetMapping("/{id}/likes")
  public Map<String, Object> getLikes(@PathVariable String id, Authentication auth) {
    Event e = repo.findById(id).orElseThrow();
    String me = null;
    if (auth != null) {
      me = users.findByPhone(auth.getName()).map(User::getId).orElse(null);
    }
    boolean likedByMe = (me != null) && e.getLikedByUserIds().contains(me);
    return Map.of("count", e.getLikeCount(), "likedByMe", likedByMe);
  }

  @PostMapping("/{id}/likes")
  public Map<String, Object> like(@PathVariable String id, Authentication auth) {
    User u = getCurrentUser(auth);
    Event e = repo.findById(id).orElseThrow();
    e.like(u.getId());
    repo.save(e);
    return Map.of("message", "liked", "count", e.getLikeCount(), "likedByMe", true);
  }

  @DeleteMapping("/{id}/likes")
  public Map<String, Object> unlike(@PathVariable String id, Authentication auth) {
    User u = getCurrentUser(auth);
    Event e = repo.findById(id).orElseThrow();
    e.unlike(u.getId());
    repo.save(e);
    return Map.of("message", "unliked", "count", e.getLikeCount(), "likedByMe", false);
  }

  // --- Helpers ---

  private User getCurrentUser(Authentication auth) {
    return users.findByPhone(auth.getName()).orElseThrow();
  }

  private void assertOwner(Authentication auth, String eventOwnerId) {
    User u = getCurrentUser(auth);
    if (!Objects.equals(eventOwnerId, u.getId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not event owner");
    }
  }

  private void assertOwner(Authentication auth, Event e) {
    assertOwner(auth, e.getCreatedByUserId());
  }

  private static void ensureParticipant(Event e, String userId) {
    e.setJoinRequests(e.getJoinRequests().stream()
        .filter(j -> !Objects.equals(j.getUserId(), userId))
        .collect(Collectors.toList()));
    e.getParticipants().add(userId);
  }

  private static Event.JoinRequest findJoinRequestForUser(Event e, String userId) {
    for (Event.JoinRequest jr : e.getJoinRequests()) {
      if (Objects.equals(jr.getUserId(), userId)) return jr;
    }
    return null;
  }

  private static Event.JoinRequest findOrCreateJoinRequest(Event e, String userId) {
    Event.JoinRequest jr = findJoinRequestForUser(e, userId);
    if (jr == null) {
      jr = new Event.JoinRequest(userId, Event.JoinStatus.PENDING, Instant.now());
      e.getJoinRequests().add(jr);
    }
    return jr;
  }

  private void approveUser(Event e, String userId) {
    boolean hasCapacity = e.getMaxParticipants() == null || e.getParticipants().size() < e.getMaxParticipants();
    if (!hasCapacity) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Event is at capacity");
    }
    Event.JoinRequest jr = findOrCreateJoinRequest(e, userId);
    jr.setStatus(Event.JoinStatus.APPROVED);
    if (jr.getRequestedAt() == null) jr.setRequestedAt(Instant.now());
    e.getParticipants().add(userId);
  }

  private void rejectUser(Event e, String userId) {
    Event.JoinRequest jr = findOrCreateJoinRequest(e, userId);
    jr.setStatus(Event.JoinStatus.REJECTED);
    if (jr.getRequestedAt() == null) jr.setRequestedAt(Instant.now());
    e.getParticipants().remove(userId);
  }
}
