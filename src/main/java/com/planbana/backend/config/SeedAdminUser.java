package com.planbana.backend.config;

import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@Configuration
public class SeedAdminUser {

    @Bean
    CommandLineRunner createAdmin(
            UserRepository userRepo,
            PasswordEncoder encoder,
            @Value("${BOOTSTRAP_ADMIN_PHONE:}") String configuredPhone,
            @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String configuredPassword) {
        return args -> {
            if (configuredPhone == null || configuredPhone.isBlank()
                    || configuredPassword == null || configuredPassword.isBlank()) {
                return;
            }

            String adminPhone = configuredPhone.trim();
            User admin = userRepo.findByPhone(adminPhone).orElse(null);
            if (admin == null) {
                admin = new User();
                admin.setPhone(adminPhone);
                admin.setFirebaseUid("bootstrap-admin");
                admin.setPasswordHash(encoder.encode(configuredPassword));
                admin.setPhoneVerified(true);
            }

            admin.setRoles(Set.of("ADMIN", "USER"));
            admin.setGovIdVerificationStatus(User.VerificationStatus.VERIFIED);
            admin.setDisabled(false);
            userRepo.save(admin);
        };
    }
}