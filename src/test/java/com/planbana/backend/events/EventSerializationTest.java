package com.planbana.backend.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class EventSerializationTest {

  @Test
  void serializesTransientHostNameForEventCards() throws Exception {
    Event event = new Event();
    event.setHostName("Alex Rivera");

    JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(event));

    assertEquals("Alex Rivera", json.path("hostName").asText());
  }
}
