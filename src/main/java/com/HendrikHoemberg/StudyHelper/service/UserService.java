package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.entity.UserRole;
import com.HendrikHoemberg.StudyHelper.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    public static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Transactional
    public User registerUser(String username, String rawPassword) {
        String normalizedUsername = normalizeUsername(username);
        validatePassword(rawPassword);

        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new IllegalArgumentException("Username already exists: " + normalizedUsername);
        }

        User user = new User();
        user.setUsername(normalizedUsername);
        user.setPassword(passwordEncoder.encode(rawPassword));
        applyDefaults(user);
        return userRepository.save(user);
    }

    @Transactional
    public void createIfNotExists(String username, String rawPassword) {
        String normalizedUsername;
        try {
            normalizedUsername = normalizeUsername(username);
        } catch (IllegalArgumentException ex) {
            return;
        }

        if (rawPassword == null || rawPassword.isBlank() || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            return;
        }

        if (userRepository.findByUsername(normalizedUsername).isEmpty()) {
            User user = new User();
            user.setUsername(normalizedUsername);
            user.setPassword(passwordEncoder.encode(rawPassword));
            applyDefaults(user);
            userRepository.save(user);
        }
    }

    @Transactional
    public void promoteToAdminIfPresent(String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        userRepository.findByUsername(username).ifPresent(user -> {
            user.setRole(UserRole.ADMIN);
            user.setEnabled(true);
            userRepository.save(user);
        });
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            throw new IllegalArgumentException("Username is required");
        }
        String normalized = username.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        return normalized;
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank() || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                "Password must be at least " + MIN_PASSWORD_LENGTH + " characters long");
        }
    }

    private void applyDefaults(User user) {
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        user.setStorageQuotaBytes(User.DEFAULT_STORAGE_QUOTA_BYTES);
        user.setDailyAiRequestLimit(User.DEFAULT_DAILY_AI_REQUEST_LIMIT);
    }
}
