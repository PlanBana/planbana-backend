// package com.planbana.backend.admin;

// import com.planbana.backend.user.User;
// import com.planbana.backend.user.UserRepository;
// import org.springframework.data.domain.*;
// import org.springframework.security.access.prepost.PreAuthorize;
// import org.springframework.web.bind.annotation.*;

// @RestController
// @RequestMapping("/api/admin/users")
// public class AdminUserController {

//     private final UserRepository users;

//     public AdminUserController(UserRepository users) {
//         this.users = users;
//     }

//     @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
//     @GetMapping("/search")
//     public Page<User> search(
//             @RequestParam(required = false) String search,
//             @RequestParam(required = false) String role,
//             @RequestParam(required = false) User.VerificationStatus verificationStatus,
//             @RequestParam(defaultValue = "0") int page,
//             @RequestParam(defaultValue = "20") int size) {
//         Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
//         return users.searchAdmin(search, role, verificationStatus, pageable);
//     }
// }

package com.planbana.backend.admin;

import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

        private final UserRepository users;

        public AdminUserController(UserRepository users) {
                this.users = users;
        }

        // DTOs
        public record UserSummaryDto(
                        String id,
                        String phone,
                        String displayName,
                        Set<String> roles,
                        User.VerificationStatus govIdVerificationStatus,
                        boolean active) {
        }

        public record UserDetailDto(
                        String id,
                        String phone,
                        String displayName,
                        String bio,
                        Set<String> roles,
                        User.VerificationStatus govIdVerificationStatus,
                        boolean active,
                        Set<String> hobbies,
                        String occupation,
                        String company) {
        }

        public record StatusUpdateRequest(
                        boolean active) {
        }

        @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
        @GetMapping
        public Map<String, Object> listUsers(
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        @RequestParam(required = false) String search,
                        @RequestParam(required = false) String role,
                        @RequestParam(required = false) User.VerificationStatus verificationStatus) {
                Pageable pageable = PageRequest.of(
                                Math.max(page, 0),
                                Math.min(size, 100),
                                Sort.by(Sort.Direction.DESC, "createdAt") // or _id
                );

                Page<User> pageResult;

                // simple filter logic – you can optimize with custom queries later
                if (search != null && !search.isBlank()) {
                        pageResult = users.searchAdmin(search.trim(), pageable, role, verificationStatus);
                } else {
                        pageResult = users.findAllAdmin(pageable, role, verificationStatus);
                }

                List<UserSummaryDto> content = pageResult.getContent().stream()
                                .map(u -> new UserSummaryDto(
                                                u.getId(),
                                                u.getPhone(),
                                                u.getDisplayName(),
                                                u.getRoles(),
                                                u.getGovIdVerificationStatus(),
                                                !Boolean.TRUE.equals(u.getDisabled()) // or isActive flag
                                ))
                                .toList();

                return Map.of(
                                "content", content,
                                "page", pageResult.getNumber(),
                                "size", pageResult.getSize(),
                                "totalElements", pageResult.getTotalElements(),
                                "totalPages", pageResult.getTotalPages());
        }

        @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
        @GetMapping("/{id}")
        public UserDetailDto getUser(@PathVariable String id) {
                User u = users.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

                return new UserDetailDto(
                                u.getId(),
                                u.getPhone(),
                                u.getDisplayName(),
                                u.getBio(),
                                u.getRoles(),
                                u.getGovIdVerificationStatus(),
                                !Boolean.TRUE.equals(u.getDisabled()),
                                u.getHobbies(),
                                u.getOccupation(),
                                u.getCompany());
        }

        // Enable / disable user account (soft-block)
        @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
        @PatchMapping("/{id}/status")
        public Map<String, Object> updateStatus(@PathVariable String id,
                        @RequestBody StatusUpdateRequest req) {
                User u = users.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

                u.setDisabled(!req.active());
                users.save(u);

                return Map.of(
                                "userId", u.getId(),
                                "active", !u.getDisabled());
        }
}
