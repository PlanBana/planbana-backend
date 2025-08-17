package com.planbana.backend.destinations;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/destinations")
public class DestinationController {
  private final DestinationRepository repo;

  public DestinationController(DestinationRepository repo) { this.repo = repo; }

  @GetMapping
  public List<Destination> list() { return repo.findAll(); }

  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping
  public Destination create(@RequestBody Destination d) { return repo.save(d); }

  @PreAuthorize("hasRole('ADMIN')")
  @PatchMapping("/{id}")
  public Map<String, String> update(@PathVariable String id, @RequestBody Destination d) {
    Destination e = repo.findById(id).orElseThrow();
    if (d.getName() != null) e.setName(d.getName());
    if (d.getCountry() != null) e.setCountry(d.getCountry());
    if (d.getDescription() != null) e.setDescription(d.getDescription());
    if (d.getImageUrl() != null) e.setImageUrl(d.getImageUrl());
    repo.save(e);
    return Map.of("message", "updated");
  }

  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{id}")
  public Map<String, String> delete(@PathVariable String id) {
    repo.deleteById(id);
    return Map.of("message", "deleted");
  }
}
