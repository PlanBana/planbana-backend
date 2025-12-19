package com.planbana.backend.admin.ratelimit;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AdminRateLimiter {

    private static class Bucket {
        Deque<Instant> timestamps = new ArrayDeque<>();
    }

    // key = adminId:action
    private final Map<String, Bucket> store = new ConcurrentHashMap<>();

    /**
     * Enforce rate limit for admin actions
     *
     * @param adminId       admin phone / id
     * @param action        action key (e.g. BLOCK_USER)
     * @param limit         max actions allowed
     * @param windowSeconds time window in seconds
     */
    public void check(
            String adminId,
            String action,
            int limit,
            int windowSeconds) {
        String key = adminId + ":" + action;
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(windowSeconds);

        Bucket bucket = store.computeIfAbsent(key, k -> new Bucket());

        synchronized (bucket) {
            // remove expired timestamps
            while (!bucket.timestamps.isEmpty()
                    && bucket.timestamps.peekFirst().isBefore(windowStart)) {
                bucket.timestamps.pollFirst();
            }

            if (bucket.timestamps.size() >= limit) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too many admin actions. Please wait before retrying.");
            }

            bucket.timestamps.addLast(now);
        }
    }
}
