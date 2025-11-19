package com.planbana.backend.events;

import com.planbana.backend.audit.AuditAction;
import com.planbana.backend.audit.AuditCategory;
import com.planbana.backend.audit.AuditLog;
import com.planbana.backend.audit.AuditLogRepository;
import com.planbana.backend.audit.AuditLogger;
import com.planbana.backend.chat.ChatRoomService;
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
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/events")
public class EventController {

  private final EventRepository repo;
  private final MongoTemplate mongo;
  private final UserRepository users;
  private final ChatRoomService chatService;
  private final AuditLogger auditLogger;
  private final AuditLogRepository auditLogRepo; // for read endpoint

  private static final Logger logger = LoggerFactory.getLogger(EventController.class);
  private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");

  public EventController(
      EventRepository repo,
      MongoTemplate mongo,
      UserRepository users,
      ChatRoomService chatService,
      AuditLogger auditLogger,
      AuditLogRepository auditLogRepo) {
    this.repo = repo;
    this.mongo = mongo;
    this.users = users;
    this.chatService = chatService;
    this.auditLogger = auditLogger;
    this.auditLogRepo = auditLogRepo;
  }

  // ================= DTOs =================

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

  // ================= Endpoints =================

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

    repo.save(e);

    // ✅ Auto-create chatroom for this event
    chatService.createIfNotExists(e.getId(), e.getTitle(), u.getId(), e.getImageUrl());

    // 🔐 Audit: event + user activity
    Map<String, Object> meta = new HashMap<>();
    meta.put("title", e.getTitle());
    meta.put("category", e.getCategory());
    meta.put("startAt", e.getStartAt());
    meta.put("pricingType", e.getPricingType());

    auditLogger.log(
        AuditCategory.EVENT_LIFECYCLE,
        AuditAction.EVENT_CREATED,
        u.getId(),
        null,
        e.getId(),
        meta);

    auditLogger.log(
        AuditCategory.USER_ACTIVITY,
        AuditAction.USER_CREATED_EVENT,
        u.getId(),
        null,
        e.getId(),
        Map.of("title", e.getTitle()));

    return e;
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
      @RequestParam(required = false) Boolean showCanceled) {
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

    if (!Boolean.TRUE.equals(showCanceled)) {
      query.addCriteria(Criteria.where("isCanceled").ne(true));
    }

    query.with(PageRequest.of(page, size));
    return mongo.find(query, Event.class);
  }

  @GetMapping("/{id}")
  public Map<String, Object> get(@PathVariable String id, Authentication auth) {
    Event e = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    boolean editable = false;
    if (auth != null) {
      String currentUserId = users.findByPhone(auth.getName()).map(User::getId).orElse(null);
      editable = e.getCreatedByUserId().equals(currentUserId)
          || e.getCoHostUserIds().contains(currentUserId);
    }
    return Map.of("event", e, "editableByMe", editable);
  }

  @GetMapping("/my")
  public List<Event> getMyEvents(Authentication auth) {
    User currentUser = getCurrentUser(auth);
    return repo.findByCreatedByUserId(currentUser.getId());
  }

  @PatchMapping("/{id}")
  public Map<String, String> update(@PathVariable String id,
      @RequestBody Map<String, Object> body,
      Authentication auth) {
    Event e = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    User currentUser = getCurrentUser(auth);
    assertOwner(auth, e);

    boolean updated = false;
    List<String> changedFields = new ArrayList<>();

    if (body.containsKey("title")) {
      e.setTitle((String) body.get("title"));
      updated = true;
      changedFields.add("title");
    }
    if (body.containsKey("description")) {
      e.setDescription((String) body.get("description"));
      updated = true;
      changedFields.add("description");
    }
    if (body.containsKey("imageUrl")) {
      e.setImageUrl((String) body.get("imageUrl"));
      updated = true;
      changedFields.add("imageUrl");
    }
    if (body.containsKey("category")) {
      e.setCategory((String) body.get("category"));
      updated = true;
      changedFields.add("category");
    }
    if (body.containsKey("pricingType")) {
      e.setPricingType((String) body.get("pricingType"));
      updated = true;
      changedFields.add("pricingType");
    }
    if (body.containsKey("price")) {
      e.setPrice((String) body.get("price"));
      updated = true;
      changedFields.add("price");
    }
    if (body.containsKey("maxParticipants")) {
      e.setMaxParticipants(Integer.parseInt(body.get("maxParticipants").toString()));
      updated = true;
      changedFields.add("maxParticipants");
    }
    if (body.containsKey("requirements")) {
      e.setRequirements((String) body.get("requirements"));
      updated = true;
      changedFields.add("requirements");
    }
    if (body.containsKey("additionalGuidelines")) {
      e.setAdditionalGuidelines((String) body.get("additionalGuidelines"));
      updated = true;
      changedFields.add("additionalGuidelines");
    }

    repo.save(e);

    if (updated) {
      Map<String, Object> meta = new HashMap<>();
      meta.put("changedFields", changedFields);

      auditLogger.log(
          AuditCategory.EVENT_LIFECYCLE,
          AuditAction.EVENT_DETAILS_UPDATED,
          currentUser.getId(),
          null,
          e.getId(),
          meta);

      auditLogger.log(
          AuditCategory.USER_ACTIVITY,
          AuditAction.USER_UPDATED_EVENT,
          currentUser.getId(),
          null,
          e.getId(),
          meta);
    }

    return Map.of("message", "updated");
  }

  @DeleteMapping("/{id}")
  public Map<String, String> delete(@PathVariable String id, Authentication auth) {
    Event e = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    User currentUser = getCurrentUser(auth);

    if (!e.isHost(currentUser.getId())) {
      logger.warn("Unauthorized delete attempt by user {} on event {}", currentUser.getId(), id);

      auditLogger.security(
          AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
          currentUser.getId(),
          Map.of(
              "endpoint", "DELETE /api/events/" + id,
              "reason", "non_host_delete_attempt"));

      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the event host can delete this event");
    }

    repo.delete(e);

    auditLogger.log(
        AuditCategory.EVENT_LIFECYCLE,
        AuditAction.EVENT_DELETED,
        currentUser.getId(),
        null,
        e.getId(),
        Map.of("reason", "host_deleted"));

    auditLogger.log(
        AuditCategory.USER_ACTIVITY,
        AuditAction.USER_DELETED_EVENT,
        currentUser.getId(),
        null,
        e.getId(),
        Map.of());

    return Map.of("message", "deleted");
  }

  @PatchMapping("/{id}/status") // Cancel or Restore Event API
  public Map<String, String> updateEventStatus(
      @PathVariable String id,
      @RequestParam String action,
      Authentication auth) {

    Event event = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    User currentUser = getCurrentUser(auth);

    if (!event.isHost(currentUser.getId())) {
      logger.warn("Unauthorized status update by user {} on event {}", currentUser.getId(), id);
      auditLogger.security(
          AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
          currentUser.getId(),
          Map.of(
              "endpoint", "PATCH /api/events/" + id + "/status",
              "reason", "non_host_status_change"));
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can modify event status");
    }

    String act = action.toLowerCase(Locale.ROOT);
    switch (act) {
      case "cancel" -> {
        if (event.isCanceled()) {
          return Map.of("message", "Event is already canceled");
        }
        event.setCanceled(true);
        auditLogger.log(
            AuditCategory.EVENT_LIFECYCLE,
            AuditAction.EVENT_CANCELED,
            currentUser.getId(),
            null,
            event.getId(),
            Map.of("action", "cancel"));
      }
      case "restore" -> {
        if (!event.isCanceled()) {
          return Map.of("message", "Event is not canceled");
        }
        event.setCanceled(false);
        auditLogger.log(
            AuditCategory.EVENT_LIFECYCLE,
            AuditAction.EVENT_RESTORED,
            currentUser.getId(),
            null,
            event.getId(),
            Map.of("action", "restore"));
      }
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Invalid action. Use 'cancel' or 'restore'");
    }

    repo.save(event);
    return Map.of("message", "Event status updated to " + action);
  }

  @PostMapping("/{id}/join")
  public JoinStatusResponse requestJoin(@PathVariable String id, Authentication auth) {
    User u = getCurrentUser(auth);
    Event e = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    // Host auto-approve
    if (Objects.equals(e.getCreatedByUserId(), u.getId())) {
      ensureParticipant(e, u.getId());
      repo.save(e);
      chatService.addParticipantIfExists(e.getId(), u.getId());

      auditLogger.log(
          AuditCategory.EVENT_JOIN,
          AuditAction.EVENT_JOIN_APPROVED,
          u.getId(),
          null,
          e.getId(),
          Map.of("reason", "host_self_join"));

      return new JoinStatusResponse(Event.JoinStatus.APPROVED.name());
    }

    // Already participant
    if (e.getParticipants().contains(u.getId())) {
      chatService.addParticipantIfExists(e.getId(), u.getId());

      auditLogger.log(
          AuditCategory.EVENT_JOIN,
          AuditAction.EVENT_JOIN_APPROVED,
          u.getId(),
          null,
          e.getId(),
          Map.of("alreadyParticipant", true));

      return new JoinStatusResponse(Event.JoinStatus.APPROVED.name());
    }

    Event.JoinRequest existing = findJoinRequestForUser(e, u.getId());
    if (existing != null) {
      // Already requested; don't spam logs every time
      return new JoinStatusResponse(existing.getJoinStatus().name());
    }

    boolean hasCapacity = e.getMaxParticipants() == null || e.getParticipants().size() < e.getMaxParticipants();

    if (hasCapacity) {
      Event.JoinRequest jr = new Event.JoinRequest(u.getId(), Event.JoinStatus.PENDING, Instant.now());
      e.getJoinRequests().add(jr);
      repo.save(e);

      auditLogger.log(
          AuditCategory.EVENT_JOIN,
          AuditAction.EVENT_JOIN_REQUESTED,
          u.getId(),
          null,
          e.getId(),
          Map.of("status", "PENDING"));

      return new JoinStatusResponse(Event.JoinStatus.PENDING.name());
    } else {
      e.getParticipantsWaitingList().add(u.getId());
      repo.save(e);

      auditLogger.log(
          AuditCategory.EVENT_JOIN,
          AuditAction.EVENT_JOIN_WAITLISTED,
          u.getId(),
          null,
          e.getId(),
          Map.of("reason", "capacity_reached"));

      return new JoinStatusResponse("Participant limit reached");
    }
  }

  @DeleteMapping("/{id}/leave")
  public Map<String, String> leaveEvent(@PathVariable String id, Authentication auth) {
    User user = getCurrentUser(auth);
    Event event = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    boolean modified = false;

    if (event.getParticipants().remove(user.getId())) {
      modified = true;
    }
    if (event.getCoHostUserIds().remove(user.getId())) {
      modified = true;
    }

    List<Event.JoinRequest> updatedRequests = event.getJoinRequests()
        .stream()
        .filter(jr -> !jr.getUserId().equals(user.getId()))
        .toList();
    if (updatedRequests.size() != event.getJoinRequests().size()) {
      event.setJoinRequests(updatedRequests);
      modified = true;
    }

    if (event.getParticipantsWaitingList() != null &&
        event.getParticipantsWaitingList().remove(user.getId())) {
      modified = true;
    }

    if (modified) {
      repo.save(event);

      auditLogger.log(
          AuditCategory.EVENT_JOIN,
          AuditAction.USER_LEFT_EVENT,
          user.getId(),
          null,
          event.getId(),
          Map.of());
    }

    return Map.of("message", "You have left the event");
  }

  @GetMapping("/joined/statuses")
  public List<JoinStatusOverview> getMyJoinedEventsStatus(Authentication auth) {
    User user = getCurrentUser(auth);
    List<Event> allEvents = repo.findAll();

    List<JoinStatusOverview> result = new ArrayList<>();

    for (Event e : allEvents) {
      String status;

      if (e.getParticipants().contains(user.getId())) {
        status = Event.JoinStatus.APPROVED.name();
      } else {
        Event.JoinRequest jr = e.getJoinRequests().stream()
            .filter(req -> req.getUserId().equals(user.getId()))
            .findFirst()
            .orElse(null);
        if (jr == null)
          continue;
        status = jr.getJoinStatus().name();
      }

      JoinStatusOverview overview = new JoinStatusOverview();
      overview.setEventId(e.getId());
      overview.setEventTitle(e.getTitle());
      overview.setJoinStatus(status);
      overview.setRedirectToEventPage("/events/" + e.getId());

      if (Event.JoinStatus.APPROVED.name().equals(status)) {
        overview.setConversationLink("/chat/" + e.getId());
      }
      result.add(overview);
    }

    return result;
  }

  @GetMapping("/{id}/join/requests")
  public List<Event.JoinRequest> listJoinRequests(@PathVariable String id, Authentication auth) {
    Event e = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    assertOwner(auth, e);
    return e.getJoinRequests();
  }

  @PatchMapping("/{id}/join/{userId}")
  public Map<String, Object> changeJoinStatus(
      @PathVariable String id,
      @PathVariable String userId,
      @RequestParam String action,
      Authentication auth) {

    Event e = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    User currentUser = getCurrentUser(auth);
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
      chatService.addParticipantIfExists(e.getId(), userId);
    } else if (newStatus == Event.JoinStatus.PENDING) {
      Event.JoinRequest jr = new Event.JoinRequest(userId, Event.JoinStatus.PENDING, Instant.now());
      e.getJoinRequests().add(jr);
    } else if (newStatus == Event.JoinStatus.WAITLISTED) {
      e.getParticipantsWaitingList().add(userId);
    }

    repo.save(e);

    AuditAction auditAction;
    if (newStatus == Event.JoinStatus.APPROVED) {
      auditAction = AuditAction.EVENT_JOIN_APPROVED;
    } else if (newStatus == Event.JoinStatus.REJECTED) {
      auditAction = AuditAction.EVENT_JOIN_REJECTED;
    } else if (newStatus == Event.JoinStatus.WAITLISTED) {
      auditAction = AuditAction.EVENT_JOIN_WAITLISTED;
    } else if (newStatus == Event.JoinStatus.PENDING) {
      auditAction = AuditAction.EVENT_JOIN_REQUESTED;
    } else {
      auditAction = AuditAction.EVENT_JOIN_CANCELLED;
    }

    auditLogger.log(
        AuditCategory.EVENT_JOIN,
        auditAction,
        currentUser.getId(),
        userId,
        e.getId(),
        Map.of("newStatus", newStatus != null ? newStatus.name() : "NONE"));

    return Map.of(
        "message", "updated",
        "status", newStatus != null ? newStatus.name() : "NONE",
        "participantsCount", e.getParticipants().size(),
        "spotsLeft", e.getSpotsLeft());
  }

  @GetMapping("/{id}/likes")
  public Map<String, Object> getLikes(@PathVariable String id, Authentication auth) {
    Event e = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    if (auth == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    User currentUser = getCurrentUser(auth);

    if (!e.isHost(currentUser.getId())) {
      logger.warn("Unauthorized attempt to view likes by user {} on event {}", currentUser.getId(), id);

      auditLogger.security(
          AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
          currentUser.getId(),
          Map.of(
              "endpoint", "GET /api/events/" + id + "/likes",
              "reason", "non_host_view_likes"));

      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the event host can view likes");
    }

    boolean likedByMe = e.getLikedByUserIds().contains(currentUser.getId());
    return Map.of(
        "count", e.getLikeCount(),
        "likedByMe", likedByMe);
  }

  @GetMapping("/liked")
  public List<Event> getLikedEventsForUser(Authentication auth) {
    User user = getCurrentUser(auth);

    List<Event> allEvents = repo.findAll();
    return allEvents.stream()
        .filter(e -> !e.isCanceled())
        .filter(e -> e.getLikedByUserIds().contains(user.getId()))
        .collect(Collectors.toList());
  }

  @PostMapping("/{id}/likes")
  public Map<String, Object> like(@PathVariable String id, Authentication auth) {
    User user = getCurrentUser(auth);
    Event event = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    boolean added = event.like(user.getId());
    if (added) {
      auditLogger.log(
          AuditCategory.EVENT_LIKE,
          AuditAction.EVENT_LIKED,
          user.getId(),
          null,
          event.getId(),
          Map.of("newCount", event.getLikeCount()));
    }

    repo.save(event);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("message", "liked");
    response.put("likedByMe", true);
    if (event.isHost(user.getId())) {
      response.put("count", event.getLikeCount());
    }

    return response;
  }

  @DeleteMapping("/{id}/likes")
  public Map<String, Object> unlike(@PathVariable String id, Authentication auth) {
    User user = getCurrentUser(auth);
    Event event = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    boolean removed = event.unlike(user.getId());
    if (removed) {
      auditLogger.log(
          AuditCategory.EVENT_LIKE,
          AuditAction.EVENT_UNLIKED,
          user.getId(),
          null,
          event.getId(),
          Map.of("newCount", event.getLikeCount()));
    }

    repo.save(event);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("message", "unliked");
    response.put("likedByMe", false);
    if (event.isHost(user.getId())) {
      response.put("count", event.getLikeCount());
    }

    return response;
  }

  @PostMapping("/{id}/cohosts/{userId}")
  public Map<String, String> updateCoHost(@PathVariable String id,
      @PathVariable String userId,
      @RequestParam String action,
      Authentication auth) {
    Event e = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    User currentUser = getCurrentUser(auth);

    if (!e.isHost(currentUser.getId())) {
      logger.warn("Unauthorized co-host modification by non-host: {}", currentUser.getId());

      auditLogger.security(
          AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
          currentUser.getId(),
          Map.of(
              "endpoint", "POST /api/events/" + id + "/cohosts/" + userId,
              "reason", "non_host_cohost_management"));

      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the event host can manage co-hosts");
    }

    if ("add".equalsIgnoreCase(action)) {
      if (!e.getParticipants().contains(userId)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "User must be an APPROVED participant to become co-host");
      }
      boolean added = e.getCoHostUserIds().add(userId);
      repo.save(e);

      if (added) {
        auditLogger.log(
            AuditCategory.EVENT_COHOSTS,
            AuditAction.EVENT_COHOST_ADDED,
            currentUser.getId(),
            userId,
            e.getId(),
            Map.of());
      }

      return Map.of("message", added ? "co-host added" : "already a co-host");
    } else if ("remove".equalsIgnoreCase(action)) {
      if (e.isHost(userId)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Host cannot be removed");
      }
      boolean removed = e.getCoHostUserIds().remove(userId);
      repo.save(e);

      if (removed) {
        auditLogger.log(
            AuditCategory.EVENT_COHOSTS,
            AuditAction.EVENT_COHOST_REMOVED,
            currentUser.getId(),
            userId,
            e.getId(),
            Map.of());
      }

      return Map.of("message", removed ? "co-host removed" : "user was not a co-host");
    }

    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action must be 'add' or 'remove'");
  }

  @GetMapping("/{id}/participants")
  public List<Map<String, Object>> getEventParticipants(
      @PathVariable String id,
      Authentication auth) {
    Event e = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    User currentUser = getCurrentUser(auth);

    boolean isHost = e.isHost(currentUser.getId());
    boolean isCoHost = e.isCoHost(currentUser.getId());
    boolean isParticipant = e.getParticipants().contains(currentUser.getId());

    if (!isHost && !isCoHost && !isParticipant) {
      auditLogger.security(
          AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
          currentUser.getId(),
          Map.of(
              "endpoint", "GET /api/events/" + id + "/participants",
              "reason", "not_host_or_participant"));

      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "You must be a host or participant to view attendees.");
    }

    return e.getParticipants().stream()
        .map(uid -> users.findById(uid))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(u -> {
          Map<String, Object> map = new HashMap<>();
          map.put("id", u.getId());
          String name = Optional.ofNullable(u.getDisplayName())
              .orElse(Optional.ofNullable(u.getName()).orElse("Unnamed"));
          map.put("name", name);
          map.put("profileImage", u.getAvatarUrl());
          return map;
        })
        .collect(Collectors.toList());
  }

  @DeleteMapping("/{eventId}/participants/{userId}")
  public Map<String, String> removeParticipant(
      @PathVariable String eventId,
      @PathVariable String userId,
      Authentication auth) {

    User currentUser = getCurrentUser(auth);
    Event event = repo.findById(eventId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    boolean isHost = event.isHost(currentUser.getId());
    boolean isCoHost = event.isCoHost(currentUser.getId());

    if (!isHost && !isCoHost) {
      logger.warn("Unauthorized removal attempt by {} on event {}", currentUser.getId(), event.getId());

      auditLogger.security(
          AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
          currentUser.getId(),
          Map.of(
              "endpoint", "DELETE /api/events/" + eventId + "/participants/" + userId,
              "reason", "not_host_or_cohost"));

      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only host or co-host can remove participants");
    }

    if (isCoHost) {
      if (event.isHost(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Co-host cannot remove host");
      }
      if (event.isCoHost(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Co-host cannot remove other co-hosts");
      }
    }

    boolean changed = false;

    if (event.getParticipants().remove(userId)) {
      changed = true;
    }
    if (isHost && event.getCoHostUserIds().remove(userId)) {
      changed = true;
    }
    if (event.getParticipantsWaitingList() != null &&
        event.getParticipantsWaitingList().remove(userId)) {
      changed = true;
    }

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

      auditLogger.log(
          AuditCategory.EVENT_PARTICIPANTS,
          AuditAction.EVENT_PARTICIPANT_REMOVED,
          currentUser.getId(),
          userId,
          event.getId(),
          Map.of("byHost", isHost, "byCoHost", isCoHost));
    }

    return Map.of("message", "Participant removed");
  }

  @GetMapping("/{id}/share")
  public Map<String, String> getEventShareLink(@PathVariable String id, Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required to generate share link");
    }

    Event e = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    String frontendBaseUrl = "https://planbana.com/events/";
    String shareUrl = frontendBaseUrl + e.getId();

    return Map.of("shareUrl", shareUrl);
  }

  @GetMapping("/{id}/statistics")
  public Map<String, Object> getEventStats(@PathVariable String id, Authentication auth) {
    User currentUser = getCurrentUser(auth);
    Event event = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    if (!event.isHost(currentUser.getId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the event host can access statistics.");
    }

    int participantsCount = event.getParticipants().size();
    int waitingListCount = event.getParticipantsWaitingList().size();
    long likesCount = event.getLikeCount();

    int pendingJoinRequests = (int) event.getJoinRequests().stream()
        .filter(req -> !event.getParticipants().contains(req.getUserId()))
        .filter(req -> !event.getParticipantsWaitingList().contains(req.getUserId()))
        .count();

    return Map.of(
        "participants", participantsCount,
        "waitingList", waitingListCount,
        "likes", likesCount,
        "joinRequests", pendingJoinRequests);
  }

  @GetMapping("/{id}/audit-logs")
  public List<AuditLog> getAuditLogs(@PathVariable String id, Authentication auth) {
    User currentUser = getCurrentUser(auth);
    Event event = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    if (!event.isHost(currentUser.getId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only hosts can view audit logs.");
    }

    return auditLogRepo.findByEventIdOrderByTimestampDesc(event.getId());
  }

  // ===== Bookmarks =====

  @PostMapping("/{id}/bookmark")
  public Map<String, String> bookmarkEvent(@PathVariable String id, Authentication auth) {
    User user = getCurrentUser(auth);
    Event event = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    user.getBookmarkedEventIds().add(event.getId());
    users.save(user);

    auditLogger.log(
        AuditCategory.USER_ACTIVITY,
        AuditAction.USER_BOOKMARKED_EVENT,
        user.getId(),
        null,
        event.getId(),
        Map.of());

    return Map.of("message", "Event bookmarked");
  }

  @GetMapping("/bookmarks")
  public List<Event> getBookmarkedEvents(Authentication auth) {
    User user = getCurrentUser(auth);

    if (user.getBookmarkedEventIds().isEmpty()) {
      return List.of();
    }

    return repo.findAllById(user.getBookmarkedEventIds()).stream()
        .filter(e -> !e.isCanceled())
        .collect(Collectors.toList());
  }

  @DeleteMapping("/{id}/bookmark")
  public Map<String, String> removeBookmark(@PathVariable String id, Authentication auth) {
    User user = getCurrentUser(auth);

    if (user.getBookmarkedEventIds().remove(id)) {
      users.save(user);

      auditLogger.log(
          AuditCategory.USER_ACTIVITY,
          AuditAction.USER_REMOVED_BOOKMARK,
          user.getId(),
          null,
          id,
          Map.of());

      return Map.of("message", "Bookmark removed");
    }

    return Map.of("message", "Bookmark not found");
  }

  // --- Helpers ---

  private User getCurrentUser(Authentication auth) {
    if (auth == null || auth.getName() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return users.findByPhone(auth.getName())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
  }

  private void assertOwner(Authentication auth, Event e) {
    User u = getCurrentUser(auth);
    if (!Objects.equals(e.getCreatedByUserId(), u.getId()) &&
        (e.getCoHostUserIds() == null || !e.getCoHostUserIds().contains(u.getId()))) {
      logger.warn("Unauthorized access attempt by {} on event {}", u.getId(), e.getId());

      auditLogger.security(
          AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
          u.getId(),
          Map.of(
              "endpoint", "EVENT_OWNER_CHECK",
              "eventId", e.getId()));

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

  private static long extractLeadingNumber(String s) {
    var m = java.util.regex.Pattern.compile("(\\d+)").matcher(s);
    return m.find() ? Long.parseLong(m.group(1)) : 0L;
  }
}
