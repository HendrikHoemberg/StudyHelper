package com.HendrikHoemberg.StudyHelper.service;

import com.HendrikHoemberg.StudyHelper.entity.AiRequestUsage;
import com.HendrikHoemberg.StudyHelper.entity.User;
import com.HendrikHoemberg.StudyHelper.repository.AiRequestUsageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class AiRequestQuotaService {

    private final AiRequestUsageRepository aiRequestUsageRepository;
    private final Clock clock;

    @Autowired
    public AiRequestQuotaService(AiRequestUsageRepository aiRequestUsageRepository) {
        this(aiRequestUsageRepository, Clock.system(ZoneId.of("America/Los_Angeles")));
    }

    AiRequestQuotaService(AiRequestUsageRepository aiRequestUsageRepository, Clock clock) {
        this.aiRequestUsageRepository = aiRequestUsageRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public int todayUsed(User user) {
        LocalDate today = LocalDate.now(clock);
        return aiRequestUsageRepository.findByUserAndUsageDate(user, today)
            .map(AiRequestUsage::getRequestCount)
            .orElse(0);
    }

    @Transactional
    public void checkAndRecord(User user) {
        checkAndRecord(user, 1);
    }

    @Transactional
    public void checkAndRecord(User user, int requestCount) {
        if (requestCount < 1) {
            throw new IllegalArgumentException("AI request amount must be positive.");
        }
        LocalDate today = LocalDate.now(clock);
        AiRequestUsage usage = lockOrCreateUsageRow(user, today);
        if (usage.getRequestCount() + requestCount > user.getDailyAiRequestLimit()) {
            throw new AiQuotaExceededException("Daily AI request limit reached.");
        }
        usage.setRequestCount(usage.getRequestCount() + requestCount);
        aiRequestUsageRepository.save(usage);
    }

    @Transactional
    public void refund(User user, int requestCount) {
        if (requestCount < 1) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        aiRequestUsageRepository.findByUserAndUsageDateForUpdate(user, today).ifPresent(usage -> {
            usage.setRequestCount(Math.max(0, usage.getRequestCount() - requestCount));
            aiRequestUsageRepository.save(usage);
        });
    }

    private AiRequestUsage lockOrCreateUsageRow(User user, LocalDate today) {
        return aiRequestUsageRepository.findByUserAndUsageDateForUpdate(user, today)
            .orElseGet(() -> createAndLockUsageRow(user, today));
    }

    private AiRequestUsage createAndLockUsageRow(User user, LocalDate today) {
        AiRequestUsage created = new AiRequestUsage();
        created.setUser(user);
        created.setUsageDate(today);
        created.setRequestCount(0);
        try {
            return aiRequestUsageRepository.save(created);
        } catch (DataIntegrityViolationException ex) {
            return aiRequestUsageRepository.findByUserAndUsageDateForUpdate(user, today)
                .orElseThrow(() -> ex);
        }
    }
}
