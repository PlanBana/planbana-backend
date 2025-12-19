package com.planbana.backend.events;

import com.planbana.backend.admin.settings.MaintenanceGuard;
import com.planbana.backend.admin.settings.SystemSettings;
import com.planbana.backend.admin.settings.SystemSettingsService;
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
  private final AuditLogRepository auditLogRepo;

  private final MaintenanceGuard maintenanceGuard;
  private final SystemSettingsService settingsService;

  private static final Logger logger = LoggerFactory.getLogger(EventController.class);

  public EventController(
      EventRepository repo,
      MongoTemplate mongo,
      UserRepository users,
      ChatRoomService chatService,
      AuditLogger auditLogger,
      AuditLogRepository auditLogRepo,
      MaintenanceGuard maintenanceGuard,
      SystemSettingsService settingsService) {
    this.repo = repo;
    this.mongo = mongo;
    this.users = users;
    this.chatService = chatService;
    this.auditLogger = auditLogger;
    this.auditLogRepo = auditLogRepo;
    this.maintenanceGuard = maintenanceGuard;
    this.settingsService = settingsService;
  }

  // ============================================================
  // DTOs
  // ============================================================

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

  // ============================================================
  // Create Event
  // ============================================================

  @PostMapping
  public Event create(@RequestBody CreateEvent req, Authentication auth) {
    maintenanceGuard.blockIfMaintenance(auth);

    User u = getCurrentUser(auth);

    SystemSettings settings = settingsService.get();
    long count = repo.countByCreatedByUserId(u.getId());

    if (count >= settings.getMaxEventsPerUser()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "EVENT_LIMIT_REACHED");
    }

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

    // Host auto-add
    e.getParticipants().add(u.getId());

    repo.save(e);

    // Create chatroom
    chatService.createIfNotExists(e.getId(), e.getTitle(), u.getId(), e.getImageUrl());

    // AUDIT: EVENT_CREATED
    auditLogger.log(
        AuditCategory.EVENT_LIFECYCLE,
        AuditAction.EVENT_CREATED,
        u.getId(),
        null,
        e.getId(),
        Map.of(
            "title", e.getTitle(),
            "category", e.getCategory(),
            "startAt", e.getStartAt(),
            "pricingType", e.getPricingType()));

    // AUDIT: USER_CREATED_EVENT
    auditLogger.log(
        AuditCategory.USER_ACTIVITY,
        AuditAction.USER_CREATED_EVENT,
        u.getId(),
        null,
        e.getId(),
        Map.of("title", e.getTitle()));

    return e;
  }

  // ============================================================
  // List Events (search + filters)
  // ============================================================

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

    SystemSettings settings = settingsService.get();
    if (settings.isMaintenanceMode()) {
      return List.of(); // ✅ UX decision A
    }

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

    if (startDate != null)
      query.addCriteria(Criteria.where("startAt").gte(startDate));
    if (endDate != null)
      query.addCriteria(Criteria.where("endAt").lte(endDate));

    if (!Boolean.TRUE.equals(showCanceled)) {
      query.addCriteria(Criteria.where("isCanceled").ne(true));
    }

    query.with(PageRequest.of(page, size));
    return mongo.find(query, Event.class);
  }

  // ============================================================
  // Get Single Event
  // ============================================================

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

  // ============================================================
  // Get My Events
  // ============================================================

  @GetMapping("/my")
  public List<Event> getMyEvents(Authentication auth) {
    User currentUser = getCurrentUser(auth);
    return repo.findByCreatedByUserId(currentUser.getId());
  }

  // ============================================================
  // Update Event (Host only)
  // ============================================================

  @PatchMapping("/{id}")
  public Map<String, String> update(
      @PathVariable String id,
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
      changedFields.add("title");
      updated = true;
    }
    if (body.containsKey("description")) {
      e.setDescription((String) body.get("description"));
      changedFields.add("description");
      updated = true;
    }
    if (body.containsKey("imageUrl")) {
      e.setImageUrl((String) body.get("imageUrl"));
      changedFields.add("imageUrl");
      updated = true;
    }
    if (body.containsKey("category")) {
      e.setCategory((String) body.get("category"));
      changedFields.add("category");
      updated = true;
    }
    if (body.containsKey("pricingType")) {
      e.setPricingType((String) body.get("pricingType"));
      changedFields.add("pricingType");
      updated = true;
    }
    if (body.containsKey("price")) {
      e.setPrice((String) body.get("price"));
      changedFields.add("price");
      updated = true;
    }
    if (body.containsKey("maxParticipants")) {
      e.setMaxParticipants(Integer.parseInt(body.get("maxParticipants").toString()));
      changedFields.add("maxParticipants");
      updated = true;
    }
    if (body.containsKey("requirements")) {
      e.setRequirements((String) body.get("requirements"));
      changedFields.add("requirements");
      updated = true;
    }
    if (body.containsKey("additionalGuidelines")) {
      e.setAdditionalGuidelines((String) body.get("additionalGuidelines"));
      changedFields.add("additionalGuidelines");
      updated = true;
    }

    repo.save(e);

    if (updated) {
      Map<String, Object> meta = Map.of("changedFields", changedFields);

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

  // ============================================================
  // Delete Event (Host only)
  // ============================================================

  @DeleteMapping("/{id}")
  public Map<String, String> delete(@PathVariable String id, Authentication auth) {
    Event e = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    User currentUser = getCurrentUser(auth);

    if (!e.isHost(currentUser.getId())) {
      auditLogger.security(
          AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
          currentUser.getId(),
          Map.of("endpoint", "DELETE /api/events/" + id));
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

  // ============================================================
  // Cancel / Restore (Host only)
  // ============================================================

  @PatchMapping("/{id}/status")
  public Map<String, String> updateEventStatus(
      @PathVariable String id,
      @RequestParam String action,
      Authentication auth) {

    Event event = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    User currentUser = getCurrentUser(auth);

    if (!event.isHost(currentUser.getId())) {
      auditLogger.security(
          AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
          currentUser.getId(),
          Map.of("endpoint", "PATCH /api/events/" + id + "/status"));
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can modify event status");
    }

    switch (action.toLowerCase(Locale.ROOT)) {
      case "cancel" -> {
        if (event.isCanceled())
          return Map.of("message", "Event already canceled");

        event.setCanceled(true);
        repo.save(event);

        auditLogger.log(
            AuditCategory.EVENT_LIFECYCLE,
            AuditAction.EVENT_CANCELED,
            currentUser.getId(),
            null,
            event.getId(),
            Map.of("action", "cancel"));
      }
      case "restore" -> {
        if (!event.isCanceled())
          return Map.of("message", "Event is not canceled");

        event.setCanceled(false);
        repo.save(event);

        auditLogger.log(
            AuditCategory.EVENT_LIFECYCLE,
            AuditAction.EVENT_RESTORED,
            currentUser.getId(),
            null,
            event.getId(),
            Map.of("action", "restore"));
      }
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid action");
    }

    return Map.of("message", "Event status updated");
  }

  // ============================================================
  // Join Event
  // ============================================================

  @PostMapping("/{id}/join")
  public JoinStatusResponse requestJoin(@PathVariable String id, Authentication auth) {

    maintenanceGuard.blockIfMaintenance(auth);

    User u = getCurrentUser(auth);
    Event e = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    SystemSettings settings = settingsService.get();

    int max = e.getMaxParticipants() != null
        ? e.getMaxParticipants()
        : settings.getMaxEventParticipants();

    if (e.getParticipants().size() >= max) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "EVENT_FULL");
    }

    // Host auto approve
    if (Objects.equals(e.getCreatedByUserId(), u.getId())) {
      ensureApproved(e, u.getId());
      repo.save(e);
      chatService.addParticipantIfExists(e.getId(), u.getId());

      auditLogger.log(
          AuditCategory.EVENT_JOIN,
          AuditAction.EVENT_JOIN_APPROVED,
          u.getId(),
          null,
          e.getId(),
          Map.of("hostSelfJoin", true));

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

    // Check existing request
    Event.JoinRequest existing = findJoinRequest(e, u.getId());
    if (existing != null) {
      return new JoinStatusResponse(existing.getJoinStatus().name());
    }

    boolean hasCapacity = e.getMaxParticipants() == null ||
        e.getParticipants().size() < e.getMaxParticipants();

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

      return new JoinStatusResponse("WAITLISTED");
    }
  }

  // ============================================================
  // Leave Event
  // ============================================================

  @DeleteMapping("/{id}/leave")
  public Map<String, String> leaveEvent(@PathVariable String id, Authentication auth) {
    User user = getCurrentUser(auth);
    Event event = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    boolean changed = false;

    if (event.getParticipants().remove(user.getId()))
      changed = true;
    if (event.getCoHostUserIds().remove(user.getId()))
      changed = true;

    List<Event.JoinRequest> updated = event.getJoinRequests().stream()
        .filter(j -> !j.getUserId().equals(user.getId()))
        .toList();

    if (updated.size() != event.getJoinRequests().size()) {
      event.setJoinRequests(updated);
      changed = true;
    }

    if (event.getParticipantsWaitingList().remove(user.getId()))
      changed = true;

    if (changed) {
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

  // ============================================================
  // Join Requests (Host only)
  // ============================================================

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

    Event.JoinStatus newStatus = switch (action.toLowerCase(Locale.ROOT)) {
      case "approve" -> Event.JoinStatus.APPROVED;
      case "reject" -> Event.JoinStatus.REJECTED;
      case "pending" -> Event.JoinStatus.PENDING;
      case "waitlist", "waiting" -> Event.JoinStatus.WAITLISTED;
      case "none", "clear" -> null;
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid join status action");
    };

    // Reset old status
    e.getParticipants().remove(userId);
    e.getJoinRequests().removeIf(j -> j.getUserId().equals(userId));
    e.getParticipantsWaitingList().remove(userId);

    // Apply new status
    if (newStatus == Event.JoinStatus.APPROVED) {
      e.getParticipants().add(userId);
      chatService.addParticipantIfExists(e.getId(), userId);
    } else if (newStatus == Event.JoinStatus.PENDING) {
      e.getJoinRequests().add(new Event.JoinRequest(userId, Event.JoinStatus.PENDING, Instant.now()));
    } else if (newStatus == Event.JoinStatus.WAITLISTED) {
      e.getParticipantsWaitingList().add(userId);
    }

    repo.save(e);

    // Select correct audit action
    AuditAction auditAction = switch (newStatus) {
      case APPROVED -> AuditAction.EVENT_JOIN_APPROVED;
      case REJECTED -> AuditAction.EVENT_JOIN_REJECTED;
      case WAITLISTED -> AuditAction.EVENT_JOIN_WAITLISTED;
      case PENDING -> AuditAction.EVENT_JOIN_REQUESTED;
      default -> AuditAction.EVENT_JOIN_CANCELLED;
    };

    auditLogger.log(
        AuditCategory.EVENT_JOIN,
        auditAction,
        currentUser.getId(),
        userId,
        e.getId(),
        Map.of("newStatus", (newStatus != null ? newStatus.name() : "NONE")));

    return Map.of(
        "message", "updated",
        "status", (newStatus != null ? newStatus.name() : "NONE"),
        "participantsCount", e.getParticipants().size(),
        "spotsLeft", e.getSpotsLeft());
  }

  // ============================================================
  // Likes
  // ============================================================

  @GetMapping("/{id}/likes")
  public Map<String, Object> getLikes(@PathVariable String id, Authentication auth) {
    Event e = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    User currentUser = getCurrentUser(auth);

    if (!e.isHost(currentUser.getId())) {
      auditLogger.security(
          AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
          currentUser.getId(),
          Map.of("endpoint", "GET /api/events/" + id + "/likes"));
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only hosts can view likes");
    }

    boolean likedByMe = e.getLikedByUserIds().contains(currentUser.getId());

    return Map.of("count", e.getLikeCount(), "likedByMe", likedByMe);
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

    Map<String, Object> result = new HashMap<>();
    result.put("message", "liked");
    result.put("likedByMe", true);

    if (event.isHost(user.getId()))
      result.put("count", event.getLikeCount());
    return result;
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

    Map<String, Object> result = new HashMap<>();
    result.put("message", "unliked");
    result.put("likedByMe", false);

    if (event.isHost(user.getId()))
      result.put("count", event.getLikeCount());
    return result;
  }

  // ============================================================
  // Co-host Management
  // ============================================================

  @PostMapping("/{id}/cohosts/{userId}")
  public Map<String, String> updateCoHost(
      @PathVariable String id,
      @PathVariable String userId,
      @RequestParam String action,
      Authentication auth) {
    Event e = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    User currentUser = getCurrentUser(auth);

    if (!e.isHost(currentUser.getId())) {
      auditLogger.security(
          AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
          currentUser.getId(),
          Map.of("endpoint", "POST /api/events/" + id + "/cohosts/" + userId));
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only host can manage co-hosts");
    }

    if ("add".equalsIgnoreCase(action)) {

      if (!e.getParticipants().contains(userId)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User must be approved participant");
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

      return Map.of("message", added ? "cohost added" : "already cohost");

    } else if ("remove".equalsIgnoreCase(action)) {

      if (e.isHost(userId)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot remove host");
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

      return Map.of("message", removed ? "cohost removed" : "not cohost");
    }

    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action must be add/remove");
  }

  // ============================================================
  // Participants
  // ============================================================

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
          Map.of("endpoint", "GET /api/events/" + id + "/participants"));
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Must be participant or host");
    }

    return e.getParticipants().stream()
        .map(uid -> users.findById(uid))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(u -> {
          Map<String, Object> map = new HashMap<>();
          map.put("id", u.getId());
          map.put("name", Optional.ofNullable(u.getDisplayName()).orElse(u.getName()));
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
      auditLogger.security(
          AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
          currentUser.getId(),
          Map.of("endpoint", "DELETE /api/events/" + eventId + "/participants/" + userId));
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only host/cohost can remove participants");
    }

    if (isCoHost && (event.isHost(userId) || event.isCoHost(userId))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Co-host cannot remove host/co-host");
    }

    boolean changed = false;

    if (event.getParticipants().remove(userId))
      changed = true;
    if (isHost && event.getCoHostUserIds().remove(userId))
      changed = true;

    List<Event.JoinRequest> updated = event.getJoinRequests().stream().filter(j -> !j.getUserId().equals(userId))
        .toList();

    if (updated.size() != event.getJoinRequests().size()) {
      event.setJoinRequests(updated);
      changed = true;
    }

    if (event.getParticipantsWaitingList().remove(userId))
      changed = true;

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

  // ============================================================
  // Bookmarks
  // ============================================================

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

  // ============================================================
  // Event Audit Logs (host only)
  // ============================================================

  @GetMapping("/{id}/audit-logs")
  public List<AuditLog> getAuditLogs(@PathVariable String id, Authentication auth) {
    User currentUser = getCurrentUser(auth);
    Event event = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    if (!event.isHost(currentUser.getId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only host may view audit logs");
    }

    return auditLogRepo.findByEventIdOrderByTimestampDesc(event.getId());
  }

  // ============================================================
  // Helpers
  // ============================================================

  private User getCurrentUser(Authentication auth) {
    if (auth == null || auth.getName() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    return users.findByPhone(auth.getName())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
  }

  private void assertOwner(Authentication auth, Event e) {
    User u = getCurrentUser(auth);
    boolean isOwner = Objects.equals(e.getCreatedByUserId(), u.getId());
    boolean isCoHost = e.getCoHostUserIds().contains(u.getId());

    if (!isOwner && !isCoHost) {
      auditLogger.security(
          AuditAction.UNAUTHORIZED_ACCESS_ATTEMPT,
          u.getId(),
          Map.of("endpoint", "EVENT_OWNER_CHECK", "eventId", e.getId()));
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized");
    }
  }

  private static Event.JoinRequest findJoinRequest(Event e, String userId) {
    return e.getJoinRequests().stream()
        .filter(j -> j.getUserId().equals(userId))
        .findFirst()
        .orElse(null);
  }

  private static void ensureApproved(Event e, String userId) {
    e.setJoinRequests(
        e.getJoinRequests().stream().filter(j -> !j.getUserId().equals(userId)).toList());
    e.getParticipants().add(userId);
  }
}
