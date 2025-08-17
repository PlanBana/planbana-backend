package com.planbana.backend.events;

import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public Double lat;
    public Double lng;
    public Set<String> tags;
  }

  @PostMapping
  public Event create(@RequestBody CreateEvent req, Authentication auth) {
    User u = users.findByEmail(auth.getName()).orElseThrow();
    Event e = new Event();
    e.setTitle(req.title);
    e.setDescription(req.description);
    e.setStartAt(req.startAt);
    e.setEndAt(req.endAt);
    if (req.lng != null && req.lat != null) {
      e.setLocation(new GeoJsonPoint(req.lng, req.lat));
    }
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
      @RequestParam(defaultValue = "20") int size
  ) {
    Query query = new Query();
    if (q != null && !q.isBlank()) {
      query.addCriteria(new Criteria().orOperator(
          Criteria.where("title").regex(q, "i"),
          Criteria.where("description").regex(q, "i"),
          Criteria.where("tags").in(q)
      ));
    }
    if (lat != null && lng != null) {
      query.addCriteria(Criteria.where("location").nearSphere(new org.springframework.data.geo.Point(lng, lat))
          .maxDistance(radiusKm / 6378.1)); // radians
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
    // Simple update (in production you would check ownership/roles)
    if (body.containsKey("title")) e.setTitle((String) body.get("title"));
    if (body.containsKey("description")) e.setDescription((String) body.get("description"));
    repo.save(e);
    return Map.of("message", "updated");
  }

  @DeleteMapping("/{id}")
  public Map<String, String> delete(@PathVariable String id) {
    repo.deleteById(id);
    return Map.of("message", "deleted");
  }
}
