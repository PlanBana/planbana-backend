package com.planbana.backend.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserAdminSearchRepository {
    Page<User> searchAdmin(String search, Pageable pageable, String role, User.VerificationStatus verificationStatus);

    Page<User> findAllAdmin(Pageable pageable, String role, User.VerificationStatus verificationStatus);
}
