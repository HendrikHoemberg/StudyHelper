package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.dto.RegistrationCodeSummary;
import com.HendrikHoemberg.StudyHelper.entity.RegistrationCode;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.RegistrationCodeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class RegistrationCodeService {

    public static final Duration EXPIRY_DURATION = Duration.ofDays(3);
    public static final String INVALID_CODE_MESSAGE = "The registration code is invalid or expired.";

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 24;
    private static final int GENERATE_CODE_MAX_ATTEMPTS = 3;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RegistrationCodeRepository registrationCodeRepository;
    private final Clock clock;

    @Autowired
    public RegistrationCodeService(RegistrationCodeRepository registrationCodeRepository) {
        this(registrationCodeRepository, Clock.systemUTC());
    }

    RegistrationCodeService(RegistrationCodeRepository registrationCodeRepository, Clock clock) {
        this.registrationCodeRepository = registrationCodeRepository;
        this.clock = clock;
    }

    @Transactional
    public String generateCode(User createdBy) {
        if (createdBy == null) {
            throw new IllegalArgumentException("Creator is required");
        }

        for (int attempt = 1; attempt <= GENERATE_CODE_MAX_ATTEMPTS; attempt++) {
            Instant now = Instant.now(clock);
            String rawCode = generateRawCode();

            RegistrationCode registrationCode = new RegistrationCode();
            registrationCode.setCodeHash(hash(rawCode));
            registrationCode.setCreatedAt(now);
            registrationCode.setExpiresAt(now.plus(EXPIRY_DURATION));
            registrationCode.setCreatedBy(createdBy);
            try {
                registrationCodeRepository.save(registrationCode);
                return rawCode;
            } catch (DataIntegrityViolationException ex) {
                if (attempt == GENERATE_CODE_MAX_ATTEMPTS) {
                    throw ex;
                }
            }
        }
        throw new IllegalStateException("Failed to generate registration code");
    }

    @Transactional
    public void consume(String rawCode, User newUser) {
        if (newUser == null) {
            throw new IllegalArgumentException("User is required");
        }

        RegistrationCode registrationCode = registrationCodeRepository
            .findFirstByCodeHashAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(hash(rawCode))
            .orElseThrow(() -> new IllegalArgumentException(INVALID_CODE_MESSAGE));

        Instant now = Instant.now(clock);
        int updated = registrationCodeRepository.markAsUsedIfConsumable(
            registrationCode.getId(),
            now,
            newUser,
            now
        );
        if (updated == 0) {
            throw new IllegalArgumentException(INVALID_CODE_MESSAGE);
        }
    }

    @Transactional
    public void revoke(Long id) {
        RegistrationCode registrationCode = registrationCodeRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Registration code not found"));

        if (registrationCode.getUsedAt() != null) {
            throw new IllegalArgumentException("Used registration codes cannot be revoked");
        }

        if (registrationCode.getRevokedAt() == null) {
            registrationCode.setRevokedAt(Instant.now(clock));
            registrationCodeRepository.save(registrationCode);
        }
    }

    @Transactional(readOnly = true)
    public List<RegistrationCodeSummary> listSummaries() {
        Instant now = Instant.now(clock);
        return registrationCodeRepository.findAllByOrderByCreatedAtDesc()
            .stream()
            .map(code -> toSummary(code, now))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<RegistrationCodeSummary> listActiveSummaries() {
        Instant now = Instant.now(clock);
        return registrationCodeRepository
            .findByUsedAtIsNullAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(now)
            .stream()
            .map(code -> toSummary(code, now))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<RegistrationCodeSummary> listHistorySummaries() {
        Instant now = Instant.now(clock);
        return registrationCodeRepository
            .findByUsedAtIsNotNullOrRevokedAtIsNotNullOrExpiresAtLessThanEqualOrderByCreatedAtDesc(now)
            .stream()
            .map(code -> toSummary(code, now))
            .toList();
    }

    private RegistrationCodeSummary toSummary(RegistrationCode code, Instant now) {
        return new RegistrationCodeSummary(
            code.getId(),
            code.getCreatedAt(),
            code.getExpiresAt(),
            code.getUsedAt(),
            code.getRevokedAt(),
            code.getCreatedBy() == null ? null : code.getCreatedBy().getUsername(),
            code.getUsedBy() == null ? null : code.getUsedBy().getUsername(),
            statusFor(code, now)
        );
    }

    private String statusFor(RegistrationCode code, Instant now) {
        if (code.getUsedAt() != null) {
            return "Used";
        }
        if (code.getRevokedAt() != null) {
            return "Revoked";
        }
        if (isExpired(code, now)) {
            return "Expired";
        }
        return "Unused";
    }

    private boolean isExpired(RegistrationCode code, Instant now) {
        return code.getExpiresAt() != null && !code.getExpiresAt().isAfter(now);
    }

    private String generateRawCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int next = SECURE_RANDOM.nextInt(CODE_ALPHABET.length());
            builder.append(CODE_ALPHABET.charAt(next));
        }
        return builder.toString();
    }

    private String hash(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException(INVALID_CODE_MESSAGE);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawCode.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}
