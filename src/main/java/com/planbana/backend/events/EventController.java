package com.planbana.backend.events;

import com.planbana.backend.events.audit.AuditLog;
import com.planbana.backend.events.audit.AuditLogRepository;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
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
import com.planbana.backend.chat.ChatRoomService;
import com.planbana.backend.chat.ChatRoom;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/events")
public class EventController {

  @Autowired
  private AuditLogRepository auditLogRepo;

  @Autowired
  private final EventRepository repo;
  private final MongoTemplate mongo;
  private final UserRepository users;
  private static final Logger logger = LoggerFactory.getLogger(EventController.class);
  private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");

  private final ChatRoomService chatService;

  public EventController(EventRepository repo, MongoTemplate mongo, UserRepository users, ChatRoomService chatService) {
    this.repo = repo;
    this.mongo = mongo;
    this.users = users;
    this.chatService = chatService;
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

    repo.save(e);

    // ✅ Auto-create chatroom for this event
    chatService.createIfNotExists(e.getId(), e.getTitle(), u.getId(), e.getImageUrl());

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
    User currentUser = getCurrentUser(auth);
    assertOwner(auth, e);

    boolean updated = false;
    if (body.containsKey("title")) {
      e.setTitle((String) body.get("title"));
      updated = true;
    }
    if (body.containsKey("description")) {
      e.setDescription((String) body.get("description"));
      updated = true;
    }
    if (body.containsKey("imageUrl")) {
      e.setImageUrl((String) body.get("imageUrl"));
      updated = true;
    }
    if (body.containsKey("category")) {
      e.setCategory((String) body.get("category"));
      updated = true;
    }
    if (body.containsKey("pricingType")) {
      e.setPricingType((String) body.get("pricingType"));
      updated = true;
    }
    if (body.containsKey("price")) {
      e.setPrice((String) body.get("price"));
      updated = true;
    }
    if (body.containsKey("maxParticipants")) {
      e.setMaxParticipants(Integer.parseInt(body.get("maxParticipants").toString()));
      updated = true;
    }
    if (body.containsKey("requirements")) {
      e.setRequirements((String) body.get("requirements"));
      updated = true;
    }
    if (body.containsKey("additionalGuidelines")) {
      e.setAdditionalGuidelines((String) body.get("additionalGuidelines"));
      updated = true;
    }

    repo.save(e);

    if (updated) {
      auditLogRepo.save(new AuditLog(
          e.getId(),
          "event_updated",
          currentUser.getId(),
          null,
          Instant.now()));
    }

    return Map.of("message", "updated");
  }

  @DeleteMapping("/{id}")
  public Map<String, String> delete(@PathVariable String id, Authentication auth) {
    Event e = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    User currentUser = getCurrentUser(auth);

    // 🚫 Only allow the actual host to delete
    if (!e.isHost(currentUser.getId())) {
      logger.warn("Unauthorized delete attempt by user {} on event {}", currentUser.getId(), id);
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the event host can delete this event");
    }

    repo.delete(e);
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

    // 🔒 Only host can cancel or restore
    if (!event.isHost(currentUser.getId())) {
      logger.warn("Unauthorized status update by user {} on event {}", currentUser.getId(), id);
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can modify event status");
    }

    switch (action.toLowerCase()) {
      case "cancel":
        if (event.isCanceled()) {
          return Map.of("message", "Event is already canceled");
        }
        event.setCanceled(true);
        auditLogRepo.save(new AuditLog(
            event.getId(), "event_canceled", currentUser.getId(), null));
        break;

      case "restore":
        if (!event.isCanceled()) {
          return Map.of("message", "Event is not canceled");
        }
        event.setCanceled(false);
        auditLogRepo.save(new AuditLog(
            event.getId(), "event_restored", currentUser.getId(), null));
        break;

      default:
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Invalid action. Use 'cancel' or 'restore'");
    }

    repo.save(event);
    return Map.of("message", "Event status updated to " + action);
  }

  // @GetMapping("/canceledInPast")
  // public List<Event> getMyCanceledEvents(Authentication auth) {
  // User currentUser = getCurrentUser(auth);
  // return repo.findByCreatedByUserIdAndCanceledTrue(currentUser.getId());
  // }

  @PostMapping("/{id}/join")
  public JoinStatusResponse requestJoin(@PathVariable String id, Authentication auth) {
    User u = getCurrentUser(auth);
    Event e = repo.findById(id).orElseThrow();

    // ✅ Auto-approve if host
    if (Objects.equals(e.getCreatedByUserId(), u.getId())) {
      ensureParticipant(e, u.getId());
      repo.save(e);

      // ✅ Sync chatroom participants
      chatService.addParticipantIfExists(e.getId(), u.getId());
      return new JoinStatusResponse(Event.JoinStatus.APPROVED.name());
    }

    // ✅ Already joined → ensure chatroom sync
    if (e.getParticipants().contains(u.getId())) {
      chatService.addParticipantIfExists(e.getId(), u.getId());
      return new JoinStatusResponse(Event.JoinStatus.APPROVED.name());
    }

    Event.JoinRequest existing = findJoinRequestForUser(e, u.getId());
    if (existing != null) {
      return new JoinStatusResponse(existing.getJoinStatus().name());
    }

    boolean hasCapacity = e.getMaxParticipants() == null || e.getParticipants().size() < e.getMaxParticipants();

    if (hasCapacity) {
      // ✅ Auto-approve if host allows instant join (optional logic)
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

  @GetMapping("/joined/statuses")
  public List<JoinStatusOverview> getMyJoinedEventsStatus(Authentication auth) {
    User user = getCurrentUser(auth);
    List<Event> allEvents = repo.findAll();

    List<JoinStatusOverview> result = new ArrayList<>();

    for (Event e : allEvents) {
      String status = null;

      if (e.getParticipants().contains(user.getId())) {
        status = Event.JoinStatus.APPROVED.name();
      } else {
        Event.JoinRequest jr = e.getJoinRequests().stream()
            .filter(req -> req.getUserId().equals(user.getId()))
            .findFirst()
            .orElse(null);

        if (jr == null)
          continue; // Skip if no join request or participation
        status = jr.getJoinStatus().name();
      }

      JoinStatusOverview overview = new JoinStatusOverview();
      overview.setEventId(e.getId());
      overview.setEventTitle(e.getTitle());
      overview.setJoinStatus(status);
      overview.setRedirectToEventPage("/events/" + e.getId());

      if (Event.JoinStatus.APPROVED.name().equals(status)) {
        overview.setConversationLink("/chat/" + e.getId()); // or actual group URL
      }

      result.add(overview);
    }

    return result;
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

    auditLogRepo.save(new AuditLog(
        e.getId(),
        "join_status_" + (newStatus != null ? newStatus.name().toLowerCase() : "cleared"),
        currentUser.getId(),
        userId,
        Instant.now()));

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

    // ✅ Allow only host to view total likes
    if (!e.isHost(currentUser.getId())) {
      logger.warn("Unauthorized attempt to view likes by user {} on event {}", currentUser.getId(), id);
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

    List<Event> allEvents = repo.findAll(); // or optimize with custom query if needed
    return allEvents.stream()
        .filter(e -> !e.isCanceled())
        .filter(e -> e.getLikedByUserIds().contains(user.getId()))
        .collect(Collectors.toList());
  }

  @PostMapping("/{id}/likes")
  public Map<String, Object> like(@PathVariable String id, Authentication auth) {
    User user = getCurrentUser(auth);
    Event event = repo.findById(id).orElseThrow();

    boolean added = event.like(user.getId());
    if (added) {
      auditLogRepo.save(new AuditLog(
          event.getId(),
          "event_liked",
          user.getId(),
          null,
          Instant.now()));
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
    Event event = repo.findById(id).orElseThrow();

    boolean removed = event.unlike(user.getId());
    if (removed) {
      auditLogRepo.save(new AuditLog(
          event.getId(),
          "event_unliked",
          user.getId(),
          null,
          Instant.now()));
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
  public Map<String, String> updateCoHost(@PathVariable String id, @PathVariable String userId,
      @RequestParam String action, Authentication auth) {
    Event e = repo.findById(id).orElseThrow();
    User currentUser = getCurrentUser(auth);

    if (!e.isHost(currentUser.getId())) {
      logger.warn("Unauthorized co-host modification by non-host: {}", currentUser.getId());
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
        auditLogRepo.save(new AuditLog(
            e.getId(),
            "cohost_added",
            currentUser.getId(),
            userId,
            Instant.now()));
      }

      return Map.of("message", added ? "co-host added" : "already a co-host");
    }

    else if ("remove".equalsIgnoreCase(action)) {
      if (e.isHost(userId)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Host cannot be removed");
      }
      boolean removed = e.getCoHostUserIds().remove(userId);
      repo.save(e);

      if (removed) {
        auditLogRepo.save(new AuditLog(
            e.getId(),
            "cohost_removed",
            currentUser.getId(),
            userId,
            Instant.now()));
      }

      return Map.of("message", removed ? "co-host removed" : "user was not a co-host");
    }

    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action must be 'add' or 'remove'");
  }

  @GetMapping("/{id}/participants")
  public List<Map<String, Object>> getEventParticipants(
      @PathVariable String id,
      Authentication auth) {
    Event e = repo.findById(id).orElseThrow();
    User currentUser = getCurrentUser(auth);

    boolean isHost = e.isHost(currentUser.getId());
    boolean isCoHost = e.isCoHost(currentUser.getId());
    boolean isParticipant = e.getParticipants().contains(currentUser.getId());

    // 🚫 Restrict visibility: only host, co-host, or participant
    if (!isHost && !isCoHost && !isParticipant) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "You must be a host or participant to view attendees.");
    }

    // ✅ Return public info of approved participants
    return e.getParticipants().stream()
        .map(uid -> users.findById(uid))
        .filter(Optional::isPresent)
        .map(opt -> opt.get()) // use explicit lambda
        .map(u -> {
          Map<String, Object> map = new HashMap<>();
          map.put("id", u.getId());
          String name = Optional.ofNullable(u.getDisplayName())
              .orElse(Optional.ofNullable(u.getName())
                  .orElse("Unnamed"));
          map.put("name", name);
          map.put("profileImage", u.getAvatarUrl());
          return map;
        })
        .collect(Collectors.toList()); // ✅ works in all Java versions

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

    if (!isHost && !isCoHost) {
      logger.warn("Unauthorized removal attempt by {} on event {}", currentUser.getId(), event.getId());
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
      auditLogRepo.save(new AuditLog(
          event.getId(),
          "participant_removed",
          currentUser.getId(),
          userId,
          Instant.now()));
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

  @GetMapping("/{id}/statistics")
  public Map<String, Object> getEventStats(@PathVariable String id, Authentication auth) {
    User currentUser = getCurrentUser(auth);
    Event event = repo.findById(id).orElseThrow();

    // ❗ Host-only access
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
    Event event = repo.findById(id).orElseThrow();

    if (!event.isHost(currentUser.getId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only hosts can view audit logs.");
    }

    return auditLogRepo.findByEventIdOrderByTimestampDesc(event.getId());
  }

  // BookMark APIs

  @PostMapping("/{id}/bookmark")
  public Map<String, String> bookmarkEvent(@PathVariable String id, Authentication auth) {
    User user = getCurrentUser(auth);
    Event event = repo.findById(id).orElseThrow();

    user.getBookmarkedEventIds().add(event.getId());
    users.save(user);

    return Map.of("message", "Event bookmarked");
  }

  // Get Bookmarked Events
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

  // Remove Bookmark
  @DeleteMapping("/{id}/bookmark")
  public Map<String, String> removeBookmark(@PathVariable String id, Authentication auth) {
    User user = getCurrentUser(auth);

    if (user.getBookmarkedEventIds().remove(id)) {
      users.save(user);
      return Map.of("message", "Bookmark removed");
    }

    return Map.of("message", "Bookmark not found");
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

  // private static Event.JoinRequest findOrCreateJoinRequest(Event e, String
  // userId) {
  // Event.JoinRequest jr = findJoinRequestForUser(e, userId);
  // if (jr == null) {
  // jr = new Event.JoinRequest(userId, Event.JoinStatus.PENDING, Instant.now());
  // e.getJoinRequests().add(jr);
  // }
  // return jr;
  // }

  // private void assertAdmin(Authentication auth, Event e) {
  // User u = getCurrentUser(auth);
  // if (!Objects.equals(e.getCreatedByUserId(), u.getId()) &&
  // (e.getCoHostUserIds() == null || !e.getCoHostUserIds().contains(u.getId())))
  // {
  // throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not event owner or
  // co-host");
  // }
  // }

  // private void approveUser(Event e, String userId) {
  // boolean hasCapacity = e.getMaxParticipants() == null ||
  // e.getParticipants().size() < e.getMaxParticipants();
  // if (!hasCapacity) {
  // throw new ResponseStatusException(HttpStatus.CONFLICT, "Event is at
  // capacity");
  // }
  // Event.JoinRequest jr = findOrCreateJoinRequest(e, userId);
  // jr.setStatus(Event.JoinStatus.APPROVED);
  // if (jr.getRequestedAt() == null)
  // jr.setRequestedAt(Instant.now());
  // e.getParticipants().add(userId);
  // }

  // private void rejectUser(Event e, String userId) {
  // Event.JoinRequest jr = findOrCreateJoinRequest(e, userId);
  // jr.setStatus(Event.JoinStatus.REJECTED);
  // if (jr.getRequestedAt() == null)
  // jr.setRequestedAt(Instant.now());
  // e.getParticipants().remove(userId);
  // }

  // // ===== Date/Time helpers (NEW) =====

  // private static Instant buildInstantFromUi(String dateStr, String timeStr) {
  // // Remove ordinal suffixes: 1st/2nd/3rd/4th → 1/2/3/4
  // String normalizedDate = dateStr
  // .replaceAll("(?i)(\\d+)(st|nd|rd|th)", "$1")
  // .trim();

  // // Example: "September 10, 2025"
  // DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMMM d, uuuu",
  // Locale.ENGLISH);

  // // Example: "11:55am" / "3pm"
  // // Handle optional minutes
  // String normalizedTime = timeStr.toUpperCase(Locale.ROOT).replaceAll("\\s+",
  // "");
  // DateTimeFormatter timeFmt = normalizedTime.contains(":")
  // ? DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH)
  // : DateTimeFormatter.ofPattern("ha", Locale.ENGLISH);

  // LocalDate date = LocalDate.parse(normalizedDate, dateFmt);
  // LocalTime time = LocalTime.parse(normalizedTime, timeFmt);

  // ZonedDateTime zdt = ZonedDateTime.of(date, time, DEFAULT_ZONE);
  // return zdt.toInstant();
  // }

  // private static Instant addDuration(Instant start, String durationStr) {
  // if (durationStr == null || durationStr.isBlank()) {
  // // Default to 1 hour if not provided
  // return start.plus(Duration.ofHours(1));
  // }
  // String s = durationStr.trim().toLowerCase(Locale.ROOT);
  // // Try "90 minutes", "90 min", "1 hour", "2 hours"
  // long minutes = 0;
  // if (s.contains("hour")) {
  // // extract first number
  // long hours = extractLeadingNumber(s);
  // minutes = hours * 60;
  // } else if (s.contains("min")) {
  // minutes = extractLeadingNumber(s);
  // } else {
  // // fallback: treat as minutes if just a number
  // try {
  // minutes = Long.parseLong(s);
  // } catch (Exception ignored) {
  // minutes = 60;
  // }
  // }
  // return start.plus(Duration.ofMinutes(minutes > 0 ? minutes : 60));
  // }

  private static long extractLeadingNumber(String s) {
    var m = java.util.regex.Pattern.compile("(\\d+)").matcher(s);
    return m.find() ? Long.parseLong(m.group(1)) : 0L;
  }
}
