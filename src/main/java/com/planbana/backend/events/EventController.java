package com.planbana.backend.events;

import com.planbana.backend.admin.settings.MaintenanceGuard;
import com.planbana.backend.admin.settings.SystemSettings;
import com.planbana.backend.admin.settings.SystemSettingsService;
import com.planbana.backend.audit.AuditAction;
import com.planbana.backend.audit.AuditCategory;
import com.planbana.backend.audit.AuditLog;
import com.planbana.backend.audit.AuditLogRepository;
import com.planbana.backend.audit.AuditLogger;
import com.planbana.backend.chat.ChatRoom;
import com.planbana.backend.chat.ChatRoomService;
import com.planbana.backend.notification.NotificationService;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
  private final NotificationService notificationService;

  private final MaintenanceGuard maintenanceGuard;
  private final SystemSettingsService settingsService;
  private final EventLifecycleService lifecycleService;

  private static final Logger logger = LoggerFactory.getLogger(EventController.class);

  public EventController(
      EventRepository repo,
      MongoTemplate mongo,
      UserRepository users,
      ChatRoomService chatService,
      AuditLogger auditLogger,
      AuditLogRepository auditLogRepo,
      NotificationService notificationService,
      MaintenanceGuard maintenanceGuard,
      SystemSettingsService settingsService,
      EventLifecycleService lifecycleService) {
    this.repo = repo;
    this.mongo = mongo;
    this.users = users;
    this.chatService = chatService;
    this.auditLogger = auditLogger;
    this.auditLogRepo = auditLogRepo;
    this.notificationService = notificationService;
    this.maintenanceGuard = maintenanceGuard;
    this.settingsService = settingsService;
    this.lifecycleService = lifecycleService;
  }

  // ============================================================
  // DTOs
  // ============================================================

  public static class CreateEvent {
    public String title, description, imageUrl, category, pricingType, price, requirements, additionalGuidelines;
    public String meetingPoint, destination;
    public Boolean outOfTown;
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

  /** Join request plus a lightweight profile summary of the requester. */
  public static class JoinRequestWithUser {
    public String userId;
    public String joinStatus;
    public Instant requestedAt;
    public String name;
    public String displayName;
    public String avatarUrl;
    public String city;
    public String occupation;
    public Double ratingAverage;
    public boolean verified;

    public static JoinRequestWithUser from(Event.JoinRequest jr, User u) {
      JoinRequestWithUser dto = new JoinRequestWithUser();
      dto.userId = jr.getUserId();
      dto.joinStatus = jr.getJoinStatus() != null ? jr.getJoinStatus().name() : null;
      dto.requestedAt = jr.getRequestedAt();
      if (u != null) {
        dto.name = u.getName();
        dto.displayName = u.getDisplayName();
        dto.avatarUrl = u.getAvatarUrl();
        dto.city = u.getCity();
        dto.occupation = u.getOccupation();
        dto.ratingAverage = u.getRatingAverage();
        dto.verified = u.getGovIdVerificationStatus() == User.VerificationStatus.VERIFIED;
      }
      return dto;
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

    e.setMeetingPoint(req.meetingPoint);
    e.setDestination(req.destination);
    e.setOutOfTown(Boolean.TRUE.equals(req.outOfTown));
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

    // Create chatroom — host + accepted participants (both identities) by default
    chatService.ensureEventGroupJoined(e, u, u);

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
    List<Event> events = mongo.find(query, Event.class);
    populateHostNames(events);
    return events;
  }

  // ============================================================
  // Trending Events (Public)
  // ============================================================

  @GetMapping("/trending")
  public Map<String, Object> trendingEvents(
      @RequestParam(defaultValue = "6") int limit,
      Authentication auth) {
    // Get events sorted by a composite score: likes + participant count
    Query query = new Query();

    // Public endpoint: a cancelled event must never be advertised again.
    // (Mirrors the `showCanceled` guard in list().)
    query.addCriteria(Criteria.where("isCanceled").ne(true));

    query.with(PageRequest.of(0, limit));

    // Sort by likeCount descending, then by participant count
    query.with(Sort.by(Sort.Direction.DESC, "likeCount")
        .and(Sort.by(Sort.Direction.DESC, "participants")));

    List<Event> events = mongo.find(query, Event.class);
    populateHostNames(events);

    // Transform to frontend-friendly format
    List<Map<String, Object>> trending = events.stream()
        .map(this::toTrendingEvent)
        .collect(Collectors.toList());

    return Map.of("data", trending);
  }

  private Map<String, Object> toTrendingEvent(Event e) {
    Map<String, Object> result = new HashMap<>();
    result.put("id", e.getId());
    result.put("title", e.getTitle());
    result.put("category", e.getCategory() != null ? e.getCategory().toLowerCase() : "general");
    result.put("location", formatLocation(e));
    result.put("date", e.getStartAt() != null ? e.getStartAt().toString() : "");
    result.put("dateLabel", formatDateLabel(e.getStartAt()));
    result.put("priceLabel", formatPrice(e));
    result.put("hostName", e.getHostName());
    result.put("organizerName", e.getHostName());
    result.put("participants", e.getJoinedParticipantCount());
    result.put("capacity", e.getMaxParticipants());
    result.put("likeCount", e.getLikeCount());
    result.put("imageUrl", e.getImageUrl());
    result.put("tags", e.getTags() != null ? new ArrayList<>(e.getTags()) : new ArrayList<>());
    return result;
  }

  private void populateHostNames(List<Event> events) {
    Set<String> hostIds = events.stream()
        .map(Event::getCreatedByUserId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

    Map<String, String> hostNames = new HashMap<>();
    if (!hostIds.isEmpty()) {
      users.findAllById(hostIds).forEach(user -> {
        String displayName = user.getDisplayName();
        String name = displayName != null && !displayName.isBlank() ? displayName : user.getName();
        hostNames.put(user.getId(), name != null && !name.isBlank() ? name : "Unknown");
      });
    }

    events.forEach(event -> event.setHostName(hostNames.getOrDefault(event.getCreatedByUserId(), "Unknown")));
  }

  private String formatLocation(Event e) {
    if (e.getLocation() != null) {
      return "Location set";
    }
    if (e.getStartLocation() != null) {
      return "Start point set";
    }
    return "Location TBD";
  }

  private String formatDateLabel(Instant instant) {
    if (instant == null)
      return "";
    ZonedDateTime zdt = instant.atZone(ZoneId.systemDefault());
    return zdt.getMonthValue() + " " + zdt.getDayOfMonth();
  }

  private String formatPrice(Event e) {
    if (e.getPricingType() == null)
      return "Free";
    return switch (e.getPricingType()) {
      case "FREE" -> "Free";
      case "PAID" -> e.getPrice() != null ? "₹" + e.getPrice() : "Paid";
      case "DONATION" -> "Pay what you want";
      default -> "Free";
    };
  }

  // ============================================================
  // Get Single Event
  // ============================================================

  @GetMapping("/{id}")
  public Map<String, Object> get(@PathVariable String id, Authentication auth) {
    Event e = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    populateHostNames(List.of(e));

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
  public List<Event> getMyEvents(
      @RequestParam(required = false, defaultValue = "created") String type,
      Authentication auth) {
    User currentUser = getCurrentUser(auth);
    String me = currentUser.getId();

    // `type=joined` — events I take part in but did not create. Only the host
    // is filtered out; co-hosts are always approved participants, so a
    // co-hosted event still shows up here (it belongs to neither list
    // otherwise, since "hosting" is strictly createdByUserId).
    if ("joined".equalsIgnoreCase(type)) {
      List<Event> events = repo.findByParticipantsContaining(me).stream()
          .filter(e -> !Objects.equals(e.getCreatedByUserId(), me))
          .toList();
      populateHostNames(events);
      return events;
    }

    List<Event> events = repo.findByCreatedByUserId(me);
    populateHostNames(events);
    return events;
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

    // Cancelled events are frozen history — restore first, then edit.
    // (Frontend also locks Edit, this is the enforced counterpart.)
    if (e.isCanceled()) {
      throw new ResponseStatusException(HttpStatus.GONE, "Event is cancelled — restore it before editing");
    }

    Instant updatedStartAt = body.containsKey("startAt")
        ? parseEventInstant(body.get("startAt"), "startAt")
        : e.getStartAt();
    Instant updatedEndAt = body.containsKey("endAt")
        ? parseEventInstant(body.get("endAt"), "endAt")
        : e.getEndAt();
    if (updatedStartAt != null && updatedEndAt != null && !updatedEndAt.isAfter(updatedStartAt)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endAt must be after startAt");
    }

    boolean updated = false;
    List<String> changedFields = new ArrayList<>();

    if (body.containsKey("startAt")) {
      e.setStartAt(updatedStartAt);
      changedFields.add("startAt");
      updated = true;
    }
    if (body.containsKey("endAt")) {
      e.setEndAt(updatedEndAt);
      changedFields.add("endAt");
      updated = true;
    }
    if (body.containsKey("meetingPoint")) {
      e.setMeetingPoint(parseLocationLabel(body.get("meetingPoint"), "meetingPoint"));
      changedFields.add("meetingPoint");
      updated = true;
    } else if (body.get("location") instanceof String locationLabel) {
      e.setMeetingPoint(parseLocationLabel(locationLabel, "location"));
      changedFields.add("meetingPoint");
      updated = true;
    }
    if (body.containsKey("destination")) {
      e.setDestination(parseLocationLabel(body.get("destination"), "destination"));
      changedFields.add("destination");
      updated = true;
    }
    if (body.containsKey("outOfTown")) {
      Object value = body.get("outOfTown");
      if (!(value instanceof Boolean outOfTown)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "outOfTown must be a boolean");
      }
      e.setOutOfTown(outOfTown);
      changedFields.add("outOfTown");
      updated = true;
    }
    if (containsAny(body, "startLat", "startLng", "lat", "lng", "startLocation")) {
      GeoJsonPoint meetingCoordinates = eventPoint(
          body,
          "startLat",
          "startLng",
          "lat",
          "lng",
          "startLocation",
          "meeting point");
      e.setLocation(meetingCoordinates);
      e.setStartLocation(meetingCoordinates);
      changedFields.add("meetingCoordinates");
      updated = true;
    }
    if (containsAny(
        body,
        "destinationLat",
        "destinationLng",
        "destLatitude",
        "destLongitude",
        "destinationLocation")) {
      e.setDestinationLocation(eventPoint(
          body,
          "destinationLat",
          "destinationLng",
          "destLatitude",
          "destLongitude",
          "destinationLocation",
          "destination"));
      changedFields.add("destinationCoordinates");
      updated = true;
    }
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
    if (body.containsKey("tags")) {
      e.setTags(parseEventTags(body.get("tags")));
      changedFields.add("tags");
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

    // A cancelled event is kept for history — restore first, then delete.
    // (Frontend Delete stays disabled on Cancelled; this enforces it.)
    if (e.isCanceled()) {
      throw new ResponseStatusException(HttpStatus.GONE, "Event is cancelled — restore it before deleting");
    }

    // Event with guests other than the host must go through Cancel, not Delete.
    // Host is an auto-member, so participants=={host} alone is still deletable.
    long guestCount = e.getParticipants() == null ? 0
        : e.getParticipants().stream().filter(pid -> !pid.equals(e.getCreatedByUserId())).count();
    if (guestCount > 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Event has participants — cancel it instead of deleting");
    }
    // An event that already started must be cancelled, never hard-deleted.
    if (e.getStartAt() != null && !e.getStartAt().isAfter(Instant.now())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Event has already started — cancel it instead of deleting");
    }

    chatService.deleteEventGroup(e.getId());
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

        // Live-sync + fan-out live in one place so the host path and the
        // moderation path can never drift apart: broadcast on the public
        // lifecycle topic, a persisted notification for every affected
        // member, and a system note in the event chat.
        lifecycleService.onCancelled(
            event, currentUser.getId(), EventLifecycleService.Initiator.HOST);
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

        // Same fan-out as cancel, mirrored: live flip + notification + chat note.
        lifecycleService.onRestored(
            event, currentUser.getId(), EventLifecycleService.Initiator.HOST);
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

    // A cancelled event never accepts new joins — enforced here (not just in
    // the UI) so feed / details / invite-link callers all get the same 410.
    if (e.isCanceled()) {
      throw new ResponseStatusException(HttpStatus.GONE, "Event was cancelled by the host — joining is disabled");
    }
    // Moderated (blocked/hidden/pending-review) events also stop accepting.
    if (e.getStatus() != Event.Status.ACTIVE) {
      throw new ResponseStatusException(HttpStatus.GONE, "Event is not accepting joins right now");
    }

    SystemSettings settings = settingsService.get();

    int max = e.getMaxParticipants() != null
        ? e.getMaxParticipants()
        : settings.getMaxEventParticipants();

    if (e.getJoinedParticipantCount() >= max) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "EVENT_FULL");
    }

    // Host auto approve
    if (Objects.equals(e.getCreatedByUserId(), u.getId())) {
      ensureApproved(e, u.getId());
      repo.save(e);
      // Auto-join the event chat group (creates it with host + accepted
      // participants if it doesn't exist yet)
      chatService.ensureEventGroupJoined(e, u, u);

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
      User host = users.findById(e.getCreatedByUserId()).orElse(u);
      // Keep the event chat group membership in sync
      chatService.ensureEventGroupJoined(e, host, u);

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
      if (existing.getJoinStatus() != Event.JoinStatus.REJECTED) {
        return new JoinStatusResponse(existing.getJoinStatus().name());
      }
      e.getJoinRequests().remove(existing);
    }

    boolean hasCapacity = e.getMaxParticipants() == null ||
        e.getJoinedParticipantCount() < e.getMaxParticipants();

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

      notifyJoinRequested(e, u);

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

    if (event.isHost(user.getId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The host cannot leave their own event");
    }

    // Absolute freeze: a cancelled event's roster is the record refunds and
    // audit are computed against, so it must not shrink after the fact.
    // (Same 410 as removeParticipant / changeJoinStatus; the frontend hides
    // Leave on a cancelled card.)
    if (event.isCanceled()) {
      throw new ResponseStatusException(
          HttpStatus.GONE, "Event was cancelled — the participant list is frozen. Restore the event to make changes.");
    }

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
  public List<JoinRequestWithUser> listJoinRequests(@PathVariable String id, Authentication auth) {
    Event e = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

    assertOwner(auth, e);

    return e.getJoinRequests().stream()
        .map(jr -> JoinRequestWithUser.from(jr,
            users.findById(jr.getUserId()).orElse(null)))
        .collect(Collectors.toList());
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

    // Roster is frozen once cancelled — approve/reject/pending/waitlist/clear
    // must restore first. Keeps the cancelled history intact.
    if (e.isCanceled()) {
      throw new ResponseStatusException(HttpStatus.GONE, "Event is cancelled — restore it before managing participants");
    }

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
      // ✅ Host accepted the request → host AND the accepted user join the
      // event chat group automatically (group mirrors all accepted users)
      users.findById(userId)
          .ifPresent(acceptedUser -> chatService.ensureEventGroupJoined(e, currentUser, acceptedUser));
    } else if (newStatus == Event.JoinStatus.PENDING) {
      e.getJoinRequests().add(new Event.JoinRequest(userId, Event.JoinStatus.PENDING, Instant.now()));
    } else if (newStatus == Event.JoinStatus.WAITLISTED) {
      e.getParticipantsWaitingList().add(userId);
    } else if (newStatus == Event.JoinStatus.REJECTED) {
      e.getJoinRequests().add(new Event.JoinRequest(userId, Event.JoinStatus.REJECTED, Instant.now()));
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

    notifyJoinDecision(e, userId, newStatus);

    return Map.of(
        "message", "updated",
        "status", (newStatus != null ? newStatus.name() : "NONE"),
        "participantsCount", e.getJoinedParticipantCount(),
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

    // Cancel freeze: likes mutate a field ON the event record (likeCount is
    // part of the public card), so a cancelled event stays frozen. Bookmarks
    // are deliberately NOT blocked — those live on the user's own profile and
    // are theirs to curate.
    if (event.isCanceled()) {
      throw new ResponseStatusException(HttpStatus.GONE, "Event was cancelled — it no longer accepts likes");
    }

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

    // Symmetric with like(): unchanged while the event is cancelled.
    if (event.isCanceled()) {
      throw new ResponseStatusException(HttpStatus.GONE, "Event was cancelled — it no longer accepts likes");
    }

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
        .filter(uid -> !e.isHost(uid))
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

    if (event.isHost(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The host cannot be removed from the event");
    }

    if (isCoHost && (event.isHost(userId) || event.isCoHost(userId))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Co-host cannot remove host/co-host");
    }

    // Cancelled events keep their roster for audit/refund history.
    if (event.isCanceled()) {
      throw new ResponseStatusException(HttpStatus.GONE, "Event is cancelled — restore it before removing participants");
    }

    boolean changed = false;
    boolean removedParticipant = event.getParticipants().contains(userId);

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

      if (removedParticipant) {
        chatService.disableParticipant(event.getId(), userId);
        notificationService.notify(
            userId,
            "EVENT_PARTICIPANT_REMOVED",
            event.getId(),
            currentUser.getId(),
            "Removed from event",
            "You were removed from \u201c" + event.getTitle()
                + "\u201d. You can request to join again from the event page.");
      }

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

  private static Instant parseEventInstant(Object value, String field) {
    if (value instanceof Instant instant) {
      return instant;
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        return Instant.parse(text);
      } catch (java.time.format.DateTimeParseException ignored) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            field + " must be an ISO-8601 timestamp");
      }
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        field + " must be an ISO-8601 timestamp");
  }

  private static String parseLocationLabel(Object value, String field) {
    if (value == null) {
      return "";
    }
    if (value instanceof String text) {
      return text.trim();
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be a string");
  }

  private static boolean containsAny(Map<String, Object> body, String... keys) {
    for (String key : keys) {
      if (body.containsKey(key)) {
        return true;
      }
    }
    return false;
  }

  private static GeoJsonPoint eventPoint(
      Map<String, Object> body,
      String latitudeKey,
      String longitudeKey,
      String fallbackLatitudeKey,
      String fallbackLongitudeKey,
      String geoJsonKey,
      String field) {
    Object latitude = body.containsKey(latitudeKey)
        ? body.get(latitudeKey)
        : body.get(fallbackLatitudeKey);
    Object longitude = body.containsKey(longitudeKey)
        ? body.get(longitudeKey)
        : body.get(fallbackLongitudeKey);

    if (latitude == null && longitude == null && body.get(geoJsonKey) != null) {
      Object pointValue = body.get(geoJsonKey);
      if (!(pointValue instanceof Map<?, ?> point)
          || !(point.get("coordinates") instanceof List<?> coordinates)
          || coordinates.size() < 2) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " coordinates are invalid");
      }
      longitude = coordinates.get(0);
      latitude = coordinates.get(1);
    }

    if (latitude == null && longitude == null) {
      return null;
    }
    if (latitude == null || longitude == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " requires both latitude and longitude");
    }

    double lat = coordinate(latitude, field + " latitude");
    double lng = coordinate(longitude, field + " longitude");
    if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " coordinates are out of range");
    }
    return new GeoJsonPoint(lng, lat);
  }

  private static double coordinate(Object value, String field) {
    if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
      return number.doubleValue();
    }
    if (value instanceof String text) {
      try {
        double parsed = Double.parseDouble(text);
        if (Double.isFinite(parsed)) {
          return parsed;
        }
      } catch (NumberFormatException ignored) {
        // Report invalid coordinates uniformly below.
      }
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be a finite number");
  }

  private static Set<String> parseEventTags(Object value) {
    if (value == null) {
      return new LinkedHashSet<>();
    }
    if (!(value instanceof Collection<?> values)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tags must be an array of strings");
    }

    Set<String> tags = new LinkedHashSet<>();
    for (Object tag : values) {
      if (!(tag instanceof String text)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tags must be an array of strings");
      }
      if (!text.isBlank()) {
        tags.add(text.trim());
      }
    }
    return tags;
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

  /** Notify the host + all co-hosts that someone requested to join. */
  private void notifyJoinRequested(Event e, User requester) {
    String requesterName = requester != null && requester.getDisplayName() != null
        ? requester.getDisplayName()
        : (requester != null && requester.getName() != null ? requester.getName() : "Someone");

    Set<String> managers = new HashSet<>();
    if (e.getCreatedByUserId() != null)
      managers.add(e.getCreatedByUserId());
    if (e.getCoHostUserIds() != null)
      managers.addAll(e.getCoHostUserIds());

    for (String managerId : managers) {
      notificationService.notify(
          managerId,
          "JOIN_REQUEST",
          e.getId(),
          requester != null ? requester.getId() : null,
          "New join request",
          requesterName + " wants to join “" + e.getTitle() + "”");
    }
  }

  /**
   * Notify the requester of the host's decision
   * (approve/reject/waitlist/pending).
   */
  private void notifyJoinDecision(Event e, String userId, Event.JoinStatus status) {
    String title;
    String body = "Regarding “" + e.getTitle() + "”";
    switch (status) {
      case APPROVED -> {
        title = "Join request approved";
        body = "You are in! You can now chat for “" + e.getTitle() + "”.";
      }
      case REJECTED -> {
        title = "Join request declined";
        body = "The host declined your request to join “" + e.getTitle() + "”.";
      }
      case WAITLISTED -> {
        title = "You're on the waitlist";
        body = "You've been added to the waitlist for “" + e.getTitle() + "”.";
      }
      default -> {
        title = "Join request update";
        body = "Your request for “" + e.getTitle() + "” was updated.";
      }
    }

    notificationService.notify(userId, status != null ? status.name() : "JOIN_UPDATE", e.getId(), null, title, body);
  }

}
