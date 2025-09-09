package com.planbana.backend.buddies;

import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/buddies")
public class BuddyController {
  private final MongoTemplate mongo;

  public BuddyController(MongoTemplate mongo) {
    this.mongo = mongo;
  }

  @GetMapping
  public List<User> find(
      @RequestParam(required = false) Set<String> interests,
      @RequestParam(required = false) Double lat,
      @RequestParam(required = false) Double lng,
      @RequestParam(required = false, defaultValue = "25") Double radiusKm,
      @RequestParam(required = false) String city
  ) {
    Query q = new Query();
    if (interests != null && !interests.isEmpty()) {
      q.addCriteria(Criteria.where("interests").in(interests));
    }
    if (city != null && !city.isBlank()) {
      q.addCriteria(Criteria.where("city").regex("^" + city + "$", "i"));
    }
    // For simplicity, we rely on stored lat/long in user doc
    // Advanced: convert to GeoJSON + geo index.
    return mongo.find(q, User.class);
  }
}
