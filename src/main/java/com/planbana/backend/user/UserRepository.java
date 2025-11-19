package com.planbana.backend.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String>, UserAdminSearchRepository {
  Optional<User> findByEmail(String email);

  Optional<User> findByPhone(String phone);

  boolean existsByPhone(String phone);

  // --- for role & verification management ---
  boolean existsByRolesContaining(String role);

  long countByRolesContaining(String role);

  long countByGovIdVerificationStatus(User.VerificationStatus status);

  long countByDisabled(boolean disabled);

  long countByCreatedAtAfter(Instant date);

  List<User> findAllByGovIdVerificationStatus(User.VerificationStatus status);

  // You can refine these later; for now, a simple all-users page:
  // Page<User> findAll(Pageable pageable);
}
