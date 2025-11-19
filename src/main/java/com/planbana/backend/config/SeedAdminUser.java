package com.planbana.backend.config;

import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;

@Configuration
public class SeedAdminUser {

    @Bean
    CommandLineRunner createAdmin(UserRepository userRepo, PasswordEncoder passwordEncoder) {
        return args -> {

            String adminPhone = "919999999999"; // 🔥 normalized version you actually log in with

            // Try to find existing user by this phone
            User admin = userRepo.findByPhone(adminPhone).orElse(null);

            if (admin == null) {
                // Create fresh admin user
                admin = new User();
                admin.setPhone(adminPhone);
                admin.setEmail("admin@planbana.com");
                admin.setName("PlanBana Admin");
                admin.setDisplayName("Admin");

                admin.setPasswordHash(passwordEncoder.encode("Admin@12345"));
                admin.setPhoneVerified(true);
                admin.setLanguages(List.of("English"));
            }

            // ✅ Ensure roles always include ADMIN + USER
            admin.setRoles(Set.of("ADMIN", "USER"));

            // Default verification / flags
            admin.setGovIdVerificationStatus(User.VerificationStatus.VERIFIED);
            admin.setDisabled(false);

            userRepo.save(admin);

            System.out.println("✅ Ensured Admin user (phone: 919999999999, password: Admin@12345)");
        };
    }
}
