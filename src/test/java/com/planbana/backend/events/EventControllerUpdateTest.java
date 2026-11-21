package com.planbana.backend.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.planbana.backend.admin.settings.MaintenanceGuard;
import com.planbana.backend.admin.settings.SystemSettingsService;
import com.planbana.backend.audit.AuditLogRepository;
import com.planbana.backend.audit.AuditLogger;
import com.planbana.backend.chat.ChatRoomService;
import com.planbana.backend.notification.NotificationService;
import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.mock;

class EventControllerUpdateTest {

  private EventRepository events;
  private UserRepository users;
  private Authentication auth;
  private EventController controller;
  private Event event;

  @BeforeEach
  void setUp() {
    events = mock(EventRepository.class);
    users = mock(UserRepository.class);
    auth = mock(Authentication.class);
    controller = new EventController(
        events,
        mock(MongoTemplate.class),
        users,
        mock(ChatRoomService.class),
        mock(AuditLogger.class),
        mock(AuditLogRepository.class),
        mock(NotificationService.class),
        mock(MaintenanceGuard.class),
        mock(SystemSettingsService.class),
        mock(EventLifecycleService.class));

    User host = new User();
    host.setId("host-id");
    when(auth.getName()).thenReturn("host-phone");
    when(users.findByPhone("host-phone")).thenReturn(Optional.of(host));

    event = new Event();
    event.setId("event-id");
    event.setCreatedByUserId("host-id");
    event.setStartAt(Instant.parse("2030-01-01T10:00:00Z"));
    event.setEndAt(Instant.parse("2030-01-01T11:00:00Z"));
    when(events.findById("event-id")).thenReturn(Optional.of(event));
  }

  @Test
  void updatePersistsScheduleRequirementsGuidelinesAndTags() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("startAt", "2030-05-12T08:30:00Z");
    body.put("endAt", "2030-05-12T11:05:00Z");
    body.put("requirements", "Bring water, Wear comfortable shoes");
    body.put("additionalGuidelines", "Arrive 10 minutes early");
    body.put("tags", List.of("Hiking", "Nature", "Hiking"));
    body.put("meetingPoint", "Updated meeting point");
    body.put("destination", "Updated destination");
    body.put("outOfTown", true);
    body.put("startLat", 37.7749);
    body.put("startLng", -122.4194);
    body.put("lat", 37.7749);
    body.put("lng", -122.4194);
    body.put("destinationLat", 34.0522);
    body.put("destinationLng", -118.2437);

    controller.update("event-id", body, auth);

    verify(events).save(event);
    assertEquals(Instant.parse("2030-05-12T08:30:00Z"), event.getStartAt());
    assertEquals(Instant.parse("2030-05-12T11:05:00Z"), event.getEndAt());
    assertEquals("Bring water, Wear comfortable shoes", event.getRequirements());
    assertEquals("Arrive 10 minutes early", event.getAdditionalGuidelines());
    assertEquals(Set.of("Hiking", "Nature"), event.getTags());
    assertEquals("Updated meeting point", event.getMeetingPoint());
    assertEquals("Updated destination", event.getDestination());
    assertEquals(true, event.isOutOfTown());
    assertEquals(-122.4194, event.getLocation().getX(), 0.000001);
    assertEquals(37.7749, event.getLocation().getY(), 0.000001);
    assertEquals(-118.2437, event.getDestinationLocation().getX(), 0.000001);
    assertEquals(34.0522, event.getDestinationLocation().getY(), 0.000001);
  }

  @Test
  void updateRejectsEndAtThatIsNotAfterStartAt() {
    Map<String, Object> body = Map.of(
        "startAt", "2030-05-12T11:00:00Z",
        "endAt", "2030-05-12T11:00:00Z");

    ResponseStatusException error = assertThrows(
        ResponseStatusException.class,
        () -> controller.update("event-id", body, auth));

    assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    verify(events, never()).save(any(Event.class));
  }
}
