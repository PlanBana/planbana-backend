package com.planbana.backend.admin;
import com.planbana.backend.user.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;


@Component
public class BootstrapAdmin {

  private final UserRepository users;

  @Value("${app.bootstrap-admin-phone:}")
  private String bootstrapPhone;   // read from application.yml

  public BootstrapAdmin(UserRepository users) {
    this.users = users;
  }

  @PostConstruct
  public void promoteInitialAdmin() {
    if (users.existsByRolesContaining("SUPER_ADMIN")) return;

    if (bootstrapPhone == null || bootstrapPhone.isBlank()) {
      System.out.println("⚠️ No bootstrap phone configured. No SUPER_ADMIN created.");
      return;
    }

    users.findByPhone(bootstrapPhone).ifPresentOrElse(u -> {
      u.getRoles().add("SUPER_ADMIN");
      users.save(u);
      System.out.println("✅ Bootstrapped SUPER_ADMIN: " + bootstrapPhone);
    }, () -> {
      System.out.println("⚠️ Bootstrap phone not found in DB. Register user first.");
    });
  }
}
