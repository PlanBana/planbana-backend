package com.planbana.backend.admin;

import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/admin/roles")
public class AdminRoleController {

  private final UserRepository users;

  public AdminRoleController(UserRepository users) {
    this.users = users;
  }

  public static class RoleUpdateRequest {
    public Set<String> roles;
  }

  @PreAuthorize("hasRole('SUPER_ADMIN')")
  @PostMapping("/{userId}")
  public Map<String, Object> updateRoles(@PathVariable String userId, @RequestBody RoleUpdateRequest req) {
    User target = users.findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

    // Prevent removing last SUPER_ADMIN
    boolean removingSuper = target.getRoles().contains("SUPER_ADMIN")
        && (req.roles == null || !req.roles.contains("SUPER_ADMIN"));
    if (removingSuper) {
      long superCount = users.countByRolesContaining("SUPER_ADMIN");
      if (superCount <= 1) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot remove the last SUPER_ADMIN");
      }
    }

    // Sanitize roles
    Set<String> allowed = Set.of("USER", "ADMIN", "SUPER_ADMIN");
    Set<String> safeRoles = new HashSet<>();
    if (req.roles != null) {
      for (String r : req.roles) {
        if (allowed.contains(r)) safeRoles.add(r);
      }
    }
    if (safeRoles.isEmpty()) safeRoles.add("USER");

    target.setRoles(safeRoles);
    users.save(target);

    return Map.of("userId", target.getId(), "roles", target.getRoles());
  }
}
