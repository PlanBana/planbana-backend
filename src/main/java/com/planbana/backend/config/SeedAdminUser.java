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
    CommandLineRunner createAdmin(UserRepository userRepo, PasswordEncoder encoder) {
        return args -> {

            String adminPhone = "919999999999";

            User admin = userRepo.findByPhone(adminPhone).orElse(null);

            if (admin == null) {
                admin = new User();
                admin.setPhone(adminPhone);
                admin.setFirebaseUid("dev-admin");
                admin.setPasswordHash(encoder.encode("Admin@12345"));
                admin.setPhoneVerified(true);
            }

            // FORCE roles to ADMIN + USER
            admin.setRoles(Set.of("ADMIN", "USER"));

            admin.setGovIdVerificationStatus(User.VerificationStatus.VERIFIED);
            admin.setDisabled(false);

            System.out.println("Before save roles = " + admin.getRoles());
            userRepo.save(admin);
            System.out.println("After save roles = " + userRepo.findByPhone(adminPhone).get().getRoles());

            System.out.println("🔥 ADMIN USER READY: phone=919999999999 pass=Admin@12345");
        };
    }
}
