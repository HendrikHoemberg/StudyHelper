package com.HendrikHoemberg.StudyHelper.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginAttemptService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final Map<String, List<Instant>> attempts = new ConcurrentHashMap<>();

    public void loginSucceeded(String key) {
        if (attempts.containsKey(key)) {
            log.info("Login succeeded for IP: {}, clearing {} failed attempt(s)", key, attempts.get(key).size());
        }
        attempts.remove(key);
    }

    public void loginFailed(String key) {
        attempts.computeIfAbsent(key, k -> new ArrayList<>()).add(Instant.now());
        int count = attempts.get(key).size();
        if (count >= MAX_ATTEMPTS) {
            log.warn("IP blocked due to excessive login attempts: {} ({} attempts in {} minutes)", key, count,
                BLOCK_DURATION.toMinutes());
        } else {
            log.warn("Login failed for IP: {} (attempt {}/{})", key, count, MAX_ATTEMPTS);
        }
    }

    public boolean isBlocked(String key) {
        cleanup(key);
        List<Instant> userAttempts = attempts.get(key);
        return userAttempts != null && userAttempts.size() >= MAX_ATTEMPTS;
    }

    private void cleanup(String key) {
        List<Instant> userAttempts = attempts.get(key);
        if (userAttempts != null) {
            Instant cutoff = Instant.now().minus(BLOCK_DURATION);
            userAttempts.removeIf(instant -> instant.isBefore(cutoff));
            if (userAttempts.isEmpty()) {
                attempts.remove(key);
            }
        }
    }

    public static String extractClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
