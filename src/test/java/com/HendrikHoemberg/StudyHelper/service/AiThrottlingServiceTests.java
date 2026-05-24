package com.HendrikHoemberg.StudyHelper.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class AiThrottlingServiceTests {

    @Test
    void throttle_allowsRequestsWithinLimitsInstantly() {
        AtomicLong virtualTime = new AtomicLong(1000);
        // Limit: 3 requests in a 10-second window (10000ms), 500ms safety buffer
        var throttler = new AiThrottlingService(3, 10000, 500, virtualTime::get);

        long start = System.currentTimeMillis();
        throttler.throttle(); // Request 1
        throttler.throttle(); // Request 2
        throttler.throttle(); // Request 3
        long duration = System.currentTimeMillis() - start;

        // Verify all 3 requests proceed instantly without blocking
        assertThat(duration).isLessThan(50);
    }

    @Test
    void throttle_blocksRequestsWhenLimitExceeded() {
        AtomicLong virtualTime = new AtomicLong(1000);
        // Limit: 2 requests in a 100ms window, 50ms safety buffer
        var throttler = new AiThrottlingService(2, 100, 50, virtualTime::get);

        throttler.throttle(); // Request 1 (at 1000ms)
        throttler.throttle(); // Request 2 (at 1000ms)

        // 3rd request is sent at 1030ms.
        // Oldest request in the queue is at 1000ms.
        // Expected wait duration: windowMs - (current - oldest) + safetyBufferMs
        // = 100 - (1030 - 1000) + 50 = 120ms.
        virtualTime.set(1030);

        long start = System.currentTimeMillis();
        throttler.throttle(); // Should trigger thread sleep of ~120ms
        long duration = System.currentTimeMillis() - start;

        // Verify the calling thread blocked/slept for the remaining duration
        assertThat(duration).isGreaterThanOrEqualTo(100);
    }

    @Test
    void throttle_prunesOldRequestsFromRollingWindow() {
        AtomicLong virtualTime = new AtomicLong(1000);
        // Limit: 2 requests in a 100ms window, 10ms safety buffer
        var throttler = new AiThrottlingService(2, 100, 10, virtualTime::get);

        throttler.throttle(); // Request 1 (at 1000ms)
        throttler.throttle(); // Request 2 (at 1000ms)

        // Advance virtual time by 150ms (greater than 100ms windowMs)
        // making preceding requests expired.
        virtualTime.set(1150);

        // 3rd request should proceed immediately with NO delay
        long start = System.currentTimeMillis();
        throttler.throttle();
        long duration = System.currentTimeMillis() - start;

        assertThat(duration).isLessThan(50);
    }

    @Test
    void throttle_threadSafetyUnderHighConcurrency() throws Exception {
        // Limit: 5 requests in a 200ms window, 10ms safety buffer
        var throttler = new AiThrottlingService(5, 200, 10, System::currentTimeMillis);

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCounter = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    throttler.throttle();
                    successCounter.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finishedCleanly = latch.await(2000, TimeUnit.MILLISECONDS);
        executor.shutdown();

        // Assert latch completed and all 8 threads executed safely
        assertThat(finishedCleanly).isTrue();
        assertThat(successCounter.get()).isEqualTo(threadCount);
    }
}
