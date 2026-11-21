package com.planbana.backend.notification;

import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository repo;
    private final UserRepository users;

    public NotificationController(NotificationRepository repo, UserRepository users) {
        this.repo = repo;
        this.users = users;
    }

    /** All notifications for the current user, newest first. */
    @GetMapping
    public List<Notification> list(Authentication auth) {
        User u = getCurrentUser(auth);
        return repo.findByRecipientUserIdOrderByCreatedAtDesc(u.getId());
    }

    /** Unread count for the bell badge. */
    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount(Authentication auth) {
        User u = getCurrentUser(auth);
        return Map.of("count", repo.countByRecipientUserIdAndReadFalse(u.getId()));
    }

    /** Mark a single notification as read (must own it). */
    @PatchMapping("/{id}/read")
    public Map<String, Object> markRead(@PathVariable String id, Authentication auth) {
        User u = getCurrentUser(auth);
        Notification n = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!n.getRecipientUserId().equals(u.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your notification");
        }
        n.setRead(true);
        repo.save(n);
        return Map.of("id", n.getId(), "read", true);
    }

    /** Mark all of the current user's notifications as read. */
    @Transactional
    @PatchMapping("/read-all")
    public Map<String, Object> markAllRead(Authentication auth) {
        User u = getCurrentUser(auth);
        List<Notification> mine = repo.findByRecipientUserIdOrderByCreatedAtDesc(u.getId());
        mine.forEach(n -> n.setRead(true));
        if (!mine.isEmpty()) {
            repo.saveAll(mine);
        }
        return Map.of("updated", mine.size());
    }

    private User getCurrentUser(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return users.findByPhone(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}