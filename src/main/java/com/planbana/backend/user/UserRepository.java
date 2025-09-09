package com.planbana.backend.user;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
  Optional<User> findByEmail(String email);
  Optional<User> findByPhone(String phone);
  boolean existsByPhone(String phone);

  // --- for role & verification management ---
  boolean existsByRolesContaining(String role);
  long countByRolesContaining(String role);
  List<User> findAllByGovIdVerificationStatus(User.VerificationStatus status);
}
