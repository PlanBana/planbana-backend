package com.planbana.backend.events;

import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/events")
public class EventController {
  private final EventRepository repo;
  private final MongoTemplate mongo;
  private final UserRepository users;
  private static final Logger logger = LoggerFactory.getLogger(EventController.class);
  private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");

  public EventController(EventRepository repo, MongoTemplate mongo, UserRepository users) {
    this.repo = repo;
    this.mongo = mongo;
    this.users = users;
  }

  public static class CreateEvent {
    public String title, description, imageUrl, category, pricingType, price, requirements, additionalGuidelines;
    public Instant startAt, endAt;
    public Double lat, lng, startLat, startLng, destinationLat, destinationLng;
    public Integer maxParticipants;
    public Set<String> tags;
  }

  public static class JoinStatusResponse {
    public String status;

    public JoinStatusResponse(String status) {
      this.status = status;
    }
  }

  @PostMapping
  public Event create(@RequestBody CreateEvent req, Authentication auth) {
    User u = getCurrentUser(auth);
    Event e = new Event();
    e.setTitle(req.title);
    e.setDescription(req.description);
    e.setImageUrl(req.imageUrl);
    e.setStartAt(Optional.ofNullable(req.startAt)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "startAt is required")));
    e.setEndAt(Optional.ofNullable(req.endAt).orElse(e.getStartAt().plus(Duration.ofHours(1))));

    if (req.lng != null && req.lat != null)
      e.setLocation(new GeoJsonPoint(req.lng, req.lat));
    if (req.startLng != null && req.startLat != null)
      e.setStartLocation(new GeoJsonPoint(req.startLng, req.startLat));
    if (req.destinationLng != null && req.destinationLat != null)
      e.setDestinationLocation(new GeoJsonPoint(req.destinationLng, req.destinationLat));

    e.setCategory(req.category);
    e.setPricingType(req.pricingType);
    e.setPrice(req.price);
    e.setMaxParticipants(req.maxParticipants);
    e.setTags(Optional.ofNullable(req.tags).orElse(Set.of()));
    e.setCreatedByUserId(u.getId());
    e.setRequirements(req.requirements);
    e.setAdditionalGuidelines(req.additionalGuidelines);

    // ✅ Automatically add creator as APPROVED participant
    e.getParticipants().add(u.getId());

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
      @RequestParam(required = false) String category,
      @RequestParam(required = false) Boolean showCanceled // ← new param
  ) {
    Query query = new Query();

    if (q != null && !q.isBlank()) {
      query.addCriteria(new Criteria().orOperator(
          Criteria.where("title").regex(q, "i"),
          Criteria.where("description").regex(q, "i"),
          Criteria.where("tags").in(q)));
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

    // ✅ Filter out canceled events by default
    if (!Boolean.TRUE.equals(showCanceled)) {
      query.addCriteria(Criteria.where("isCanceled").ne(true));
    }

    query.with(PageRequest.of(page, size));
    return mongo.find(query, Event.class);
  }

  @GetMapping("/{id}")
  public Map<String, Object> get(@PathVariable String id, Authentication auth) {
    Event e = repo.findById(id).orElseThrow();
    boolean editable = false;
    if (auth != null) {
      String currentUserId = users.findByPhone(auth.getName()).map(User::getId).orElse(null);
      editable = e.getCreatedByUserId().equals(currentUserId) || e.getCoHostUserIds().contains(currentUserId);
    }
    return Map.of("event", e, "editableByMe", editable);
  }

  @GetMapping("/my")
  public List<Event> getMyEvents(Authentication auth) {
    User currentUser = getCurrentUser(auth);
    return repo.findByCreatedByUserId(currentUser.getId());
  }

  @PatchMapping("/{id}")
  public Map<String, String> update(@PathVariable String id, @RequestBody Map<String, Object> body,
      Authentication auth) {
    Event e = repo.findById(id).orElseThrow();
    assertOwner(auth, e);
    if (body.containsKey("title"))
      e.setTitle((String) body.get("title"));
    if (body.containsKey("description"))
      e.setDescription((String) body.get("description"));
    if (body.containsKey("imageUrl"))
      e.setImageUrl((String) body.get("imageUrl"));
    if (body.containsKey("category"))
      e.setCategory((String) body.get("category"));
    if (body.containsKey("pricingType"))
      e.setPricingType((String) body.get("pricingType"));
    if (body.containsKey("price"))
      e.setPrice((String) body.get("price"));
    if (body.containsKey("maxParticipants"))
      e.setMaxParticipants(Integer.parseInt(body.get("maxParticipants").toString()));
    if (body.containsKey("requirements"))
      e.setRequirements((String) body.get("requirements"));
    if (body.containsKey("additionalGuidelines"))
      e.setAdditionalGuidelines((String) body.get("additionalGuidelines"));
    repo.save(e);
    return Map.of("message", "updated");
  }

  @DeleteMapping("/{id}")
  public Map<String, String> delete(@PathVariable String id, Authentication auth) {
    Event e = repo.findById(id).orElseThrow();
    assertOwner(auth, e);
    repo.delete(e);
    return Map.of("message", "deleted");
  }

  @DeleteMapping("/{id}/cancel")
  public Map<String, String> cancelEvent(@PathVariable String id, Authentication auth) {
    Event event = repo.findById(id).orElseThrow();
    User currentUser = getCurrentUser(auth);

    boolean isHost = event.isHost(currentUser.getId());
    boolean isCoHost = event.isCoHost(currentUser.getId());

    if (!isHost && !isCoHost) {
      logger.warn("Unauthorized cancel attempt by user {} on event {}", currentUser.getId(), id);
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the event host or co-host can cancel the event");
    }

    if (event.isCanceled()) {
      return Map.of("message", "Event is already canceled");
    }

    event.setCanceled(true);
    repo.save(event);

    logger.info("Event {} canceled by user {}", id, currentUser.getId());
    return Map.of("message", "Event canceled successfully");
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
      Event.JoinRequest jr = new Event.JoinRequest(u.getId(), Event.JoinStatus.PENDING, Instant.now());
      e.getJoinRequests().add(jr);
      repo.save(e);
      return new JoinStatusResponse(Event.JoinStatus.PENDING.name());
    } else {
      e.getParticipantsWaitingList().add(u.getId());
      repo.save(e);
      return new JoinStatusResponse("Participant limit reached");
    }
  }

  @DeleteMapping("/{id}/leave")
  public Map<String, String> leaveEvent(@PathVariable String id, Authentication auth) {
    User user = getCurrentUser(auth);
    Event event = repo.findById(id).orElseThrow();

    boolean modified = false;

    // Remove from participants
    if (event.getParticipants().remove(user.getId())) {
      modified = true;
    }

    // Remove from co-hosts
    if (event.getCoHostUserIds().remove(user.getId())) {
      modified = true;
    }

    // Remove any pending join request
    List<Event.JoinRequest> updatedRequests = event.getJoinRequests()
        .stream()
        .filter(jr -> !jr.getUserId().equals(user.getId()))
        .toList();
    if (updatedRequests.size() != event.getJoinRequests().size()) {
      event.setJoinRequests(updatedRequests);
      modified = true;
    }

    // Remove from waitlist (if exists)
    if (event.getParticipantsWaitingList() != null &&
        event.getParticipantsWaitingList().remove(user.getId())) {
      modified = true;
    }

    if (modified) {
      repo.save(event);
    }

    return Map.of("message", "You have left the event");
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
      Authentication auth) {

    Event e = repo.findById(id).orElseThrow();
    assertOwner(auth, e);

    String act = (action == null ? "" : action.trim().toLowerCase(Locale.ROOT));
    Event.JoinStatus newStatus = switch (act) {
      case "approve" -> Event.JoinStatus.APPROVED;
      case "reject" -> Event.JoinStatus.REJECTED;
      case "pending" -> Event.JoinStatus.PENDING;
      case "waiting", "waitlist", "waitlisted" -> Event.JoinStatus.WAITLISTED;
      case "none", "remove", "clear" -> null;
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "action must be one of: approve, reject, pending, waitlist, none");
    };

    e.getParticipants().remove(userId);
    e.getParticipantsWaitingList().remove(userId);
    e.getJoinRequests().removeIf(jr -> Objects.equals(jr.getUserId(), userId));

    if (newStatus == Event.JoinStatus.APPROVED) {
      e.getParticipants().add(userId);
    } else if (newStatus == Event.JoinStatus.PENDING) {
      Event.JoinRequest jr = new Event.JoinRequest(userId, Event.JoinStatus.PENDING, Instant.now());
      e.getJoinRequests().add(jr);
    } else if (newStatus == Event.JoinStatus.WAITLISTED) {
      e.getParticipantsWaitingList().add(userId);
    }
    // else: REJECTED or NONE → removed already above

    repo.save(e);
    return Map.of(
        "message", "updated",
        "status", newStatus != null ? newStatus.name() : "NONE",
        "participantsCount", e.getParticipants().size(),
        "spotsLeft", e.getSpotsLeft());
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

  @PostMapping("/{id}/cohosts/{userId}")
  public Map<String, String> updateCoHost(@PathVariable String id, @PathVariable String userId,
      @RequestParam String action, Authentication auth) {
    Event e = repo.findById(id).orElseThrow();
    User currentUser = getCurrentUser(auth);

    // ❗ Only host can add/remove co-hosts
    if (!e.isHost(currentUser.getId())) {
      logger.warn("Unauthorized co-host modification by non-host: {}", currentUser.getId());
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the event host can manage co-hosts");
    }

    // ✅ Add only if user is APPROVED participant
    if ("add".equalsIgnoreCase(action)) {
      if (!e.getParticipants().contains(userId)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "User must be an APPROVED participant to become co-host");
      }
      boolean added = e.getCoHostUserIds().add(userId);
      repo.save(e);
      return Map.of("message", added ? "co-host added" : "already a co-host");
    }

    // ✅ Remove only if target is not the host (prevent accidental deletion)
    else if ("remove".equalsIgnoreCase(action)) {
      if (e.isHost(userId)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Host cannot be removed");
      }
      boolean removed = e.getCoHostUserIds().remove(userId);
      repo.save(e);
      return Map.of("message", removed ? "co-host removed" : "user was not a co-host");
    }

    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action must be 'add' or 'remove'");
  }

  @DeleteMapping("/{eventId}/participants/{userId}")
  public Map<String, String> removeParticipant(
      @PathVariable String eventId,
      @PathVariable String userId,
      Authentication auth) {

    User currentUser = getCurrentUser(auth);
    Event event = repo.findById(eventId).orElseThrow();

    boolean isHost = event.isHost(currentUser.getId());
    boolean isCoHost = event.isCoHost(currentUser.getId());

    // Permission check
    if (!isHost && !isCoHost) {
      logger.warn("Unauthorized removal attempt by {} on event {}", currentUser.getId(), event.getId());
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only host or co-host can remove participants");
    }

    // Co-hosts can't remove host or other co-hosts
    if (isCoHost) {
      if (event.isHost(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Co-host cannot remove host");
      }
      if (event.isCoHost(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Co-host cannot remove other co-hosts");
      }
    }

    boolean changed = false;

    // Remove from participants
    if (event.getParticipants().remove(userId)) {
      changed = true;
    }

    // Remove from co-hosts (only if host)
    if (isHost && event.getCoHostUserIds().remove(userId)) {
      changed = true;
    }

    // Remove from waiting list
    if (event.getParticipantsWaitingList() != null &&
        event.getParticipantsWaitingList().remove(userId)) {
      changed = true;
    }

    // Remove join request
    List<Event.JoinRequest> updated = event.getJoinRequests()
        .stream()
        .filter(jr -> !userId.equals(jr.getUserId()))
        .toList();
    if (updated.size() != event.getJoinRequests().size()) {
      event.setJoinRequests(updated);
      changed = true;
    }

    if (changed) {
      repo.save(event);
    }

    return Map.of("message", "Participant removed");
  }

  // Deeplink

  @GetMapping("/{id}/share")
  public Map<String, String> getEventShareLink(@PathVariable String id, Authentication auth) {
    // Optional: If you only want logged-in users to access this
    if (auth == null || !auth.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required to generate share link");
    }

    Event e = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    // Basic event share URL - frontend should have routing logic to handle it
    String frontendBaseUrl = "https://planbana.com/events/"; // move to application.properties for config
    String shareUrl = frontendBaseUrl + e.getId();

    return Map.of("shareUrl", shareUrl);
  }

  // --- Helpers ---

  private User getCurrentUser(Authentication auth) {
    return users.findByPhone(auth.getName()).orElseThrow();
  }

  private void assertOwner(Authentication auth, Event e) {
    User u = getCurrentUser(auth);
    if (!Objects.equals(e.getCreatedByUserId(), u.getId()) &&
        (e.getCoHostUserIds() == null || !e.getCoHostUserIds().contains(u.getId()))) {
      logger.warn("Unauthorized access attempt by {} on event {}", u.getId(), e.getId());
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized");
    }
  }

  private static void ensureParticipant(Event e, String userId) {
    e.setJoinRequests(e.getJoinRequests().stream()
        .filter(j -> !Objects.equals(j.getUserId(), userId))
        .collect(Collectors.toList()));
    e.getParticipants().add(userId);
  }

  private static Event.JoinRequest findJoinRequestForUser(Event e, String userId) {
    for (Event.JoinRequest jr : e.getJoinRequests()) {
      if (Objects.equals(jr.getUserId(), userId))
        return jr;
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

  private void assertAdmin(Authentication auth, Event e) {
    User u = getCurrentUser(auth);
    if (!Objects.equals(e.getCreatedByUserId(), u.getId()) &&
        (e.getCoHostUserIds() == null || !e.getCoHostUserIds().contains(u.getId()))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not event owner or co-host");
    }
  }

  private void approveUser(Event e, String userId) {
    boolean hasCapacity = e.getMaxParticipants() == null || e.getParticipants().size() < e.getMaxParticipants();
    if (!hasCapacity) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Event is at capacity");
    }
    Event.JoinRequest jr = findOrCreateJoinRequest(e, userId);
    jr.setStatus(Event.JoinStatus.APPROVED);
    if (jr.getRequestedAt() == null)
      jr.setRequestedAt(Instant.now());
    e.getParticipants().add(userId);
  }

  private void rejectUser(Event e, String userId) {
    Event.JoinRequest jr = findOrCreateJoinRequest(e, userId);
    jr.setStatus(Event.JoinStatus.REJECTED);
    if (jr.getRequestedAt() == null)
      jr.setRequestedAt(Instant.now());
    e.getParticipants().remove(userId);
  }

  // ===== Date/Time helpers (NEW) =====

  private static Instant buildInstantFromUi(String dateStr, String timeStr) {
    // Remove ordinal suffixes: 1st/2nd/3rd/4th → 1/2/3/4
    String normalizedDate = dateStr
        .replaceAll("(?i)(\\d+)(st|nd|rd|th)", "$1")
        .trim();

    // Example: "September 10, 2025"
    DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH);

    // Example: "11:55am" / "3pm"
    // Handle optional minutes
    String normalizedTime = timeStr.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    DateTimeFormatter timeFmt = normalizedTime.contains(":")
        ? DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH)
        : DateTimeFormatter.ofPattern("ha", Locale.ENGLISH);

    LocalDate date = LocalDate.parse(normalizedDate, dateFmt);
    LocalTime time = LocalTime.parse(normalizedTime, timeFmt);

    ZonedDateTime zdt = ZonedDateTime.of(date, time, DEFAULT_ZONE);
    return zdt.toInstant();
  }

  private static Instant addDuration(Instant start, String durationStr) {
    if (durationStr == null || durationStr.isBlank()) {
      // Default to 1 hour if not provided
      return start.plus(Duration.ofHours(1));
    }
    String s = durationStr.trim().toLowerCase(Locale.ROOT);
    // Try "90 minutes", "90 min", "1 hour", "2 hours"
    long minutes = 0;
    if (s.contains("hour")) {
      // extract first number
      long hours = extractLeadingNumber(s);
      minutes = hours * 60;
    } else if (s.contains("min")) {
      minutes = extractLeadingNumber(s);
    } else {
      // fallback: treat as minutes if just a number
      try {
        minutes = Long.parseLong(s);
      } catch (Exception ignored) {
        minutes = 60;
      }
    }
    return start.plus(Duration.ofMinutes(minutes > 0 ? minutes : 60));
  }

  private static long extractLeadingNumber(String s) {
    var m = java.util.regex.Pattern.compile("(\\d+)").matcher(s);
    return m.find() ? Long.parseLong(m.group(1)) : 0L;
  }
}
